/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

/**
 * Connection.cpp
 *
 * Implementation of declarations in Connection.h
 *
 *      Author: swale
 */

#include "Connection.h"

#include <boost/algorithm/string.hpp>

#include "impl/ClientService.h"
#include "impl/BufferedSocketTransport.h"
#include "impl/InternalUtils.h"

#include "PreparedStatement.h"
#include "ParametersBatch.h"
#include "ClientAttribute.h"

using namespace com::pivotal::gemfirexd;
using namespace com::pivotal::gemfirexd::client;
using namespace com::pivotal::gemfirexd::client::impl;

static const char* YES_NO[] = { "Yes", "No", NULL };
static const char* NO_YES[] = { "No", "Yes", NULL };
static const char* SSL_MODES[] = { "none", "basic", "peerAuthentication", NULL };

ConnectionProperty::ConnectionProperty(const std::string& propName,
    const char* helpMessage, const char** possibleValues, const int flags) :
    m_propName(propName), m_helpMessage(helpMessage), m_possibleValues(),
    m_flags(flags) {
  if (possibleValues != NULL) {
    const char* value;
    while ((value = *possibleValues) != NULL) {
      m_possibleValues.push_back(value);
      possibleValues++;
    }
  }
}

std::map<std::string, ConnectionProperty> ConnectionProperty::s_properties;
bool ConnectionProperty::s_init = init();

bool ConnectionProperty::init() {
  char readTimeoutHelp[256];
  char keepAliveIdleHelp[256];
  char keepAliveIntvlHelp[256];
  char keepAliveCntHelp[256];
  char singleHopMaxConnHelp[256];

  ::snprintf(readTimeoutHelp, sizeof(readTimeoutHelp) - 1,
      "Timeout in seconds to wait for connection creation or reply "
          "from server (default is %d)",
      ClientAttribute::DEFAULT_LOGIN_TIMEOUT);
  ::snprintf(keepAliveIdleHelp, sizeof(keepAliveIdleHelp) - 1,
      "TCP keepalive time in seconds between two transmissions on "
          "socket in idle condition (default is %d)",
      ClientAttribute::DEFAULT_KEEPALIVE_IDLE);
  ::snprintf(keepAliveIntvlHelp, sizeof(keepAliveIntvlHelp) - 1,
      "TCP keepalive duration in seconds between successive transmissions on "
          "socket if no reply to packet sent after idle timeout (default is %d)",
      ClientAttribute::DEFAULT_KEEPALIVE_INTVL);
  ::snprintf(keepAliveCntHelp, sizeof(keepAliveCntHelp) - 1,
      "Number of retransmissions for TCP keepalive to be sent before "
          "declaring the other end to be dead (default is %d)",
      ClientAttribute::DEFAULT_KEEPALIVE_CNT);
  ::snprintf(singleHopMaxConnHelp, sizeof(singleHopMaxConnHelp) - 1,
      "The maximum connection pool size for each server when single hop is "
          "enabled (default is %d)",
      ClientAttribute::DEFAULT_SINGLE_HOP_MAX_CONN_PER_SERVER);

  addProperty_(ClientAttribute::USERNAME, "User name to connect with",
  NULL, F_IS_USER | F_IS_UTF8);
  addProperty_(ClientAttribute::USERNAME_ALT,
      "" /* empty to skip displaying it */, NULL, F_IS_USER | F_IS_UTF8);
  addProperty_(ClientAttribute::PASSWORD, "Password of the user", NULL,
      F_IS_PASSWD | F_IS_UTF8);
  addProperty_(ClientAttribute::LOAD_BALANCE,
      "Enable/Disable transparent load balancing of the connections "
          "on available servers (default enabled)", YES_NO, F_IS_BOOLEAN);
  addProperty_(ClientAttribute::SECONDARY_LOCATORS,
      "Specify additional locators or servers to try for initial "
          "connection before giving up", NULL, F_IS_UTF8);
  addProperty_(ClientAttribute::READ_TIMEOUT, readTimeoutHelp, NULL, F_NONE);
  addProperty_(ClientAttribute::KEEPALIVE_IDLE, keepAliveIdleHelp, NULL,
      F_NONE);
  addProperty_(ClientAttribute::KEEPALIVE_INTVL, keepAliveIntvlHelp,
  NULL, F_NONE);
  addProperty_(ClientAttribute::KEEPALIVE_CNT, keepAliveCntHelp, NULL, F_NONE);
  addProperty_(ClientAttribute::SINGLE_HOP_ENABLED,
      "Enable single hop queries and update/delete DML operations for "
          "partitioned tables (default disabled)", NO_YES, F_IS_BOOLEAN);
  addProperty_(ClientAttribute::SINGLE_HOP_MAX_CONNECTIONS,
      singleHopMaxConnHelp, NULL, F_NONE);
  addProperty_(ClientAttribute::DISABLE_STREAMING,
      "Disable streaming of query results from servers to client "
          "(default is false i.e. streaming is enabled)", NO_YES, F_IS_BOOLEAN);
  addProperty_(ClientAttribute::SKIP_LISTENERS, "Skip write-through/"
      "write-behind/listener and other callback invocations for DML "
      "operations from this connection (default is false)", NO_YES,
      F_IS_BOOLEAN);
  addProperty_(ClientAttribute::SKIP_CONSTRAINT_CHECKS,
      "Skip primary key, foreign key, unique and check constraint "
          "checks. For the case of primary key, an insert is converted "
          "into PUT DML so row will retain the last values without "
          "throwing a constraint violation (default is false)", NO_YES,
      F_IS_BOOLEAN);
  addProperty_(ClientAttribute::LOG_FILE,
      "Path of the log file for any client side logging using module "
          "trace flags (e.g. gemfirexd.debug.true=TraceClientHA", NULL,
      F_IS_UTF8 | F_IS_FILENAME);
  addProperty_(ClientAttribute::CLIENT_TRACE_FILE,
      "Path to the trace file for dumping DRDA protocol messages", NULL,
      F_IS_UTF8 | F_IS_FILENAME);
  addProperty_(ClientAttribute::CLIENT_TRACE_DIRECTORY,
      "Directory of the trace file for dumping DRDA protocol messages",
      NULL, F_IS_UTF8 | F_IS_FILENAME);
  addProperty_(ClientAttribute::CLIENT_TRACE_LEVEL,
      "Specify the level of tracing to be used by client as an "
          "integer (default is TRACE_ALL 0xFFFFFFFF)", NULL, F_NONE);
  addProperty_(ClientAttribute::CLIENT_TRACE_APPEND,
      "If set to true then any new trace lines are appended to "
          "exising trace file, if any, instead of overwriting the file "
          "(default is false)", NO_YES, F_IS_BOOLEAN);
  // TODO: SSL support to implement
  addProperty_(ClientAttribute::SSL,
      "Specifies the mode for SSL communication from server to client;"
          " one of basic, peerAuthentication, or none (the default)", SSL_MODES,
      F_NONE);
  addProperty_(ClientAttribute::TX_SYNC_COMMITS,
      "Wait for 2nd phase commit to complete on all nodes instead of "
          "returning as soon as current server's 2nd phase commit is "
          "done (default is false)", NO_YES, F_IS_BOOLEAN);
  addProperty_(ClientAttribute::DISABLE_TX_BATCHING,
      "Disable all batching in transactions, flushing ops immediately "
          "to detect conflicts immediately. Note that this can have a "
          "significant performance impact so turn this on only if it "
          "is really required by application (default is false)", NO_YES,
      F_IS_BOOLEAN);
  addProperty_(ClientAttribute::LOG_FILE_STAMP,
      "Log file path to which the initialization nano time is appended.",
      NULL, F_IS_UTF8 | F_IS_FILENAME);
  addProperty_(ClientAttribute::QUERY_HDFS,
      "A connection level property to enable querying data in HDFS, "
          "else only in-memory data is queried. (default false)", NO_YES,
      F_IS_BOOLEAN);
  addProperty_(ClientAttribute::DISABLE_THINCLIENT_CANCEL,
      "A connection level property. If true, then Statement.cancel() "
          "through thin driver will not be supported. The driver will "
          "not ask for statementUUID from the server.(default false)", NO_YES,
      F_IS_BOOLEAN);

  return true;
}

void ConnectionProperty::addProperty_(const std::string& propName,
    const char* helpMessage, const char** possibleValues, const int flags) {
  // check for valid property name
  if (ClientAttribute::getAllAttributes().find(propName)
      != ClientAttribute::getAllAttributes().end()) {
    s_properties.insert(
        std::make_pair(propName,
            ConnectionProperty(propName, helpMessage, possibleValues, flags)));
  } else {
    throw GET_SQLEXCEPTION(SQLState::UNKNOWN_EXCEPTION,
        std::string("Unknown connection property in initialization: ")
            + propName);
  }
}

const std::set<std::string>& ConnectionProperty::getValidPropertyNames() {
  return ClientAttribute::getAllAttributes();
}

const ConnectionProperty& ConnectionProperty::getProperty(
    const std::string& propertyName) {
  std::map<std::string, ConnectionProperty>::iterator iter;
  if ((iter = s_properties.find(propertyName)) != s_properties.end()) {
    return iter->second;
  } else {
    throw GET_SQLEXCEPTION2(SQLStateMessage::INVALID_CONNECTION_PROPERTY_MSG,
        propertyName.c_str());
  }
}

void Connection::open(const std::string& host, const int port,
    thrift::OpenConnectionArgs& connArgs) {
  // close any existing service instance first
  close();

  m_service = new ClientService(host, port, connArgs, &m_serviceId);
}

void Connection::checkProperty(const std::string& propertyName) {
  if (ClientAttribute::getAllAttributes().find(propertyName)
      == ClientAttribute::getAllAttributes().end()) {
    SQLWarning* warning = new SQLWarning(__FILE__, __LINE__,
        SQLState::INVALID_CONNECTION_PROPERTY,
        SQLStateMessage::INVALID_CONNECTION_PROPERTY_MSG.format(
            propertyName.c_str()));
    SQLWarning* existing = m_warnings.get();
    if (existing == NULL) {
      m_warnings.reset(warning);
    } else {
      existing->setNextWarning(warning);
    }
  }
}

void Connection::open(const std::string& host, int port) {
  thrift::OpenConnectionArgs args;
  open(host, port, args);
}

void Connection::open(const std::string& host, int port,
    const std::string& user, const std::string& password) {
  thrift::OpenConnectionArgs args;
  args.__set_userName(user);
  args.__set_password(password);
  open(host, port, args);
}

void Connection::open(const std::string& host, int port,
    const std::map<std::string, std::string>& properties) {
  thrift::OpenConnectionArgs args;
  args.__set_properties(properties);
  open(host, port, args);
}

void Connection::open(const std::string& host, int port,
    const std::string& user, const std::string& password,
    const std::map<std::string, std::string>& properties) {
  thrift::OpenConnectionArgs args;
  args.__set_userName(user);
  args.__set_password(password);
  args.__set_properties(properties);
  open(host, port, args);
}

void Connection::setSendBufferSize(uint32_t sz) {
  ClientService& service = checkAndGetService();

  service.getTransport()->setSendBufferSize(sz);
}

uint32_t Connection::getSendBufferSize() const {
  ClientService& service = checkAndGetService();

  return service.getTransport()->getSendBufferSize();
}

void Connection::setReceiveBufferSize(uint32_t sz) {
  ClientService& service = checkAndGetService();

  service.getTransport()->setReceiveBufferSize(sz);
}

uint32_t Connection::getReceiveBufferSize() const {
  ClientService& service = checkAndGetService();

  return service.getTransport()->getReceiveBufferSize();
}

void Connection::setConnectTimeout(int millis) {
  ClientService& service = checkAndGetService();

  service.getTransport()->getSocket()->setConnTimeout(millis);
}

int Connection::getConnectTimeout() const {
  ClientService& service = checkAndGetService();

  return service.getTransport()->getSocket()->getConnTimeout();
}

void Connection::setSendTimeout(int millis) {
  ClientService& service = checkAndGetService();

  service.getTransport()->getSocket()->setSendTimeout(millis);
}

int Connection::getSendTimeout() const {
  ClientService& service = checkAndGetService();

  return service.getTransport()->getSocket()->getSendTimeout();
}

void Connection::setReceiveTimeout(int millis) {
  ClientService& service = checkAndGetService();

  service.getTransport()->getSocket()->setRecvTimeout(millis);
}

int Connection::getReceiveTimeout() const {
  ClientService& service = checkAndGetService();

  return service.getTransport()->getSocket()->getRecvTimeout();
}

void Connection::convertOutputParameters(
    const std::map<int32_t, OutputParameter>& outputParams,
    std::map<int32_t, thrift::OutputParameter>& result) {
  for (std::map<int32_t, OutputParameter>::const_iterator iter =
      outputParams.begin(); iter != outputParams.end(); ++iter) {
    result[iter->first] = iter->second.getThriftOutputParameter();
  }
}

AutoPtr<Result> Connection::execute(const std::string& sql,
    const std::map<int32_t, OutputParameter>& outputParams,
    const StatementAttributes& attrs) {
  ClientService& service = checkAndGetService();

  AutoPtr<Result> result(new Result(service, m_serviceId, &attrs.m_attrs));
  if (outputParams.size() == 0) {
    service.execute(result->m_result, sql, EMPTY_OUT_PARAMS, attrs.m_attrs);
    return result;
  } else {
    std::map<int32_t, thrift::OutputParameter> resultOutParams;
    convertOutputParameters(outputParams, resultOutParams);
    service.execute(result->m_result, sql, resultOutParams, attrs.m_attrs);
    return result;
  }
}

AutoPtr<ResultSet> Connection::executeQuery(const std::string& sql,
    const StatementAttributes& attrs) {
  ClientService& service = checkAndGetService();

  int32_t batchSize;
  bool updatable, scrollable;
  Result::getResultSetArgs(&attrs.m_attrs, batchSize, updatable, scrollable);

  thrift::RowSet* rsp = new thrift::RowSet();
  AutoPtr<thrift::RowSet> rs(rsp);
  service.executeQuery(*rsp, sql, attrs.m_attrs);

  AutoPtr<ResultSet> resultSet(
      new ResultSet(rsp, &attrs.m_attrs, true, service, m_serviceId, batchSize,
          updatable, scrollable, true));
  rs.release();
  return resultSet;
}

int32_t Connection::executeUpdate(const std::string& sql,
    const StatementAttributes& attrs) {
  ClientService& service = checkAndGetService();

  thrift::UpdateResult result;
  service.executeUpdate(result, Utils::singleVector(sql), attrs.m_attrs);
  if (result.__isset.warnings) {
    // set back in Connection
    m_warnings.reset(new GET_SQLWARNING(result.warnings));
  }
  if (result.__isset.updateCount) {
    return result.updateCount;
  } else {
    return -1;
  }
}

AutoPtr<std::vector<int32_t> > Connection::executeBatch(
    const std::vector<std::string>& batchSQLs,
    const StatementAttributes& attrs) {
  ClientService& service = checkAndGetService();

  thrift::UpdateResult result;
  service.executeUpdate(result, batchSQLs, attrs.m_attrs);
  if (result.__isset.warnings) {
    // set back in Connection
    m_warnings.reset(new GET_SQLWARNING(result.warnings));
  }
  if (result.__isset.batchUpdateCounts) {
    return AutoPtr<std::vector<int32_t> >(
        new std::vector<int32_t>(result.batchUpdateCounts));
  } else {
    return AutoPtr<std::vector<int32_t> >(NULL);
  }
}

AutoPtr<SQLWarning> Connection::getWarnings() {
  return AutoPtr<SQLWarning>(m_warnings.get(), false);
}

AutoPtr<PreparedStatement> Connection::prepareStatement(const std::string& sql,
    const std::map<int32_t, OutputParameter>& outputParams,
    const StatementAttributes& attrs) {
  ClientService& service = checkAndGetService();

  AutoPtr<PreparedStatement> pstmt(
      new PreparedStatement(service, m_serviceId, attrs.m_attrs));
  if (outputParams.size() == 0) {
    service.prepareStatement(pstmt->m_prepResult, sql, EMPTY_OUT_PARAMS,
        attrs.m_attrs);
  } else {
    std::map<int32_t, thrift::OutputParameter> resultOutParams;
    convertOutputParameters(outputParams, resultOutParams);
    service.prepareStatement(pstmt->m_prepResult, sql, resultOutParams,
        attrs.m_attrs);
  }
  return pstmt;
}

AutoPtr<Result> Connection::prepareAndExecute(const std::string& sql,
    const Parameters& params,
    const std::map<int32_t, OutputParameter>& outputParams,
    const StatementAttributes& attrs) {
  ClientService& service = checkAndGetService();

  AutoPtr<Result> result(new Result(service, m_serviceId, &attrs.m_attrs));
  if (outputParams.size() == 0) {
    service.prepareAndExecute(result->m_result, sql,
        Utils::singleVector<thrift::Row>(params), EMPTY_OUT_PARAMS,
        attrs.m_attrs);
    return result;
  } else {
    std::map<int32_t, thrift::OutputParameter> resultOutParams;
    convertOutputParameters(outputParams, resultOutParams);
    service.prepareAndExecute(result->m_result, sql,
        Utils::singleVector<thrift::Row>(params), resultOutParams,
        attrs.m_attrs);
    return result;
  }
}

AutoPtr<Result> Connection::prepareAndExecuteBatch(const std::string& sql,
    const ParametersBatch& paramsBatch,
    const std::map<int32_t, OutputParameter>& outputParams,
    const StatementAttributes& attrs) {
  ClientService& service = checkAndGetService();

  AutoPtr<Result> result(new Result(service, m_serviceId, &attrs.m_attrs));
  if (outputParams.size() == 0) {
    service.prepareAndExecute(result->m_result, sql, paramsBatch.m_batch,
        EMPTY_OUT_PARAMS, attrs.m_attrs);
    return result;
  } else {
    std::map<int32_t, thrift::OutputParameter> resultOutParams;
    convertOutputParameters(outputParams, resultOutParams);
    service.prepareAndExecute(result->m_result, sql, paramsBatch.m_batch,
        resultOutParams, attrs.m_attrs);
    return result;
  }
}

// transactions

void Connection::beginTransaction(const IsolationLevel::type isolationLevel) {
  ClientService& service = checkAndGetService();

  service.beginTransaction(isolationLevel);
}

IsolationLevel::type Connection::getCurrentIsolationLevel() const {
  ClientService& service = checkAndGetService();

  return service.getCurrentIsolationLevel();
}

void Connection::setTransactionAttribute(const TransactionAttribute::type flag,
    bool isTrue) {
  ClientService& service = checkAndGetService();

  service.setTransactionAttribute(flag, isTrue);
}

bool Connection::getTransactionAttribute(
    const TransactionAttribute::type flag) {
  ClientService& service = checkAndGetService();

  return service.getTransactionAttribute(flag);
}

void Connection::commitTransaction(bool startNewTransaction) {
  ClientService& service = checkAndGetService();

  service.commitTransaction(startNewTransaction);
}

void Connection::rollbackTransaction(bool startNewTransaction) {
  ClientService& service = checkAndGetService();

  service.rollbackTransaction(startNewTransaction);
}

void Connection::prepareCommitTransaction() {
  ClientService& service = checkAndGetService();

  service.prepareCommitTransaction();
}

const std::string Connection::getNativeSQL(const std::string& sql) const {
  // GFXD can handle the escape syntax directly so only needs escape
  // processing for { ? = CALL  ....}
  std::string trimSql = boost::algorithm::trim_copy(sql);
  if (trimSql.size() > 0 && trimSql[0] == '{') {
    size_t pos = trimSql.rfind('}');
    if (pos != std::string::npos) {
      return trimSql.substr(1, pos - 1);
    }
  }

  return trimSql;
}

ResultSetHoldability::type Connection::getResultSetHoldability() const throw () {
  if (m_defaultHoldability == ResultSetHoldability::NONE) {
    return thrift::g_gfxd_constants.DEFAULT_RESULTSET_HOLD_CURSORS_OVER_COMMIT
        ? ResultSetHoldability::HOLD_CURSORS_OVER_COMMIT
        : ResultSetHoldability::CLOSE_CURSORS_OVER_COMMIT;
  } else {
    return m_defaultHoldability;
  }
}

void Connection::setResultSetHoldability(
    ResultSetHoldability::type holdability) throw () {
  m_defaultHoldability = holdability;
}

// metadata API

AutoPtr<DatabaseMetaData> Connection::getServiceMetaData() {
  ClientService& service = checkAndGetService();

  if (m_metadata.get() == NULL) {
    AutoPtr<DatabaseMetaData> metadata(new DatabaseMetaData());
    service.getServiceMetaData(metadata->m_metadata);
    m_metadata = metadata;
  }
  return AutoPtr<DatabaseMetaData>(m_metadata.get(), false);
}

AutoPtr<ResultSet> Connection::getSchemaMetaData(
    const DatabaseMetaDataCall::type method, DatabaseMetaDataArgs& args,
    const DriverType::type driverType) {
  ClientService& service = checkAndGetService();

  thrift::RowSet* rsp = new thrift::RowSet();
  AutoPtr<thrift::RowSet> rs(rsp);
  args.m_args.driverType = driverType;
  service.getSchemaMetaData(*rsp, method, args.m_args);

  AutoPtr<ResultSet> resultSet(
      new ResultSet(rsp, NULL, true, service, m_serviceId, -1, false, false,
          true));
  rs.release();
  return resultSet;
}

AutoPtr<ResultSet> Connection::getIndexInfo(DatabaseMetaDataArgs& args,
    bool unique, bool approximate, const DriverType::type driverType) {
  ClientService& service = checkAndGetService();

  thrift::RowSet* rsp = new thrift::RowSet();
  AutoPtr<thrift::RowSet> rs(rsp);
  args.m_args.driverType = driverType;
  service.getIndexInfo(*rsp, args.m_args, unique, approximate);

  AutoPtr<ResultSet> resultSet(
      new ResultSet(rsp, NULL, true, service, m_serviceId, -1, false, false,
          true));
  rs.release();
  return resultSet;
}

AutoPtr<ResultSet> Connection::getUDTs(DatabaseMetaDataArgs& args,
    const std::string& typeNamePattern, const std::vector<SQLType::type>& types,
    const DriverType::type driverType) {
  ClientService& service = checkAndGetService();

  thrift::RowSet* rsp = new thrift::RowSet();
  AutoPtr<thrift::RowSet> rs(rsp);
  args.m_args.driverType = driverType;
  service.getUDTs(*rsp, args.m_args, types);

  AutoPtr<ResultSet> resultSet(
      new ResultSet(rsp, NULL, true, service, m_serviceId, -1, false, false,
          true));
  rs.release();
  return resultSet;
}

AutoPtr<ResultSet> Connection::getBestRowIdentifier(DatabaseMetaDataArgs& args,
    int32_t scope, bool nullable, const DriverType::type driverType) {
  ClientService& service = checkAndGetService();

  thrift::RowSet* rsp = new thrift::RowSet();
  AutoPtr<thrift::RowSet> rs(rsp);
  args.m_args.driverType = driverType;
  service.getBestRowIdentifier(*rsp, args.m_args, scope, nullable);

  AutoPtr<ResultSet> resultSet(
      new ResultSet(rsp, NULL, true, service, m_serviceId, -1, false, false,
          true));
  rs.release();
  return resultSet;
}

// end metadata API

void Connection::close() {
  ClientService* service = m_service;
  if (service != NULL) {
    // decrement service reference in all cases
    ClearService clr = { m_serviceId, &m_service };

    service->close();
  }
}

Connection::~Connection() throw () {
  // destructor should *never* throw an exception
  try {
    close();
  } catch (const SQLException& sqle) {
    Utils::handleExceptionInDestructor("connection", sqle);
  } catch (const std::exception& stde) {
    Utils::handleExceptionInDestructor("connection", stde);
  } catch (...) {
    Utils::handleExceptionInDestructor("connection");
  }
}

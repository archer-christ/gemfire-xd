/**
 * Autogenerated by Thrift Compiler (0.9.1)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */

package com.pivotal.gemfirexd.thrift;

public class gfxdConstants {

  public static final byte RESULTSET_TYPE_FORWARD_ONLY = (byte)1;

  public static final byte RESULTSET_TYPE_INSENSITIVE = (byte)2;

  public static final byte RESULTSET_TYPE_SENSITIVE = (byte)3;

  public static final byte RESULTSET_TYPE_UNKNOWN = (byte)4;

  public static final boolean DEFAULT_AUTOCOMMIT = false;

  public static final byte DEFAULT_RESULTSET_TYPE = (byte)1;

  public static final boolean DEFAULT_RESULTSET_UPDATABLE = false;

  public static final boolean DEFAULT_RESULTSET_HOLD_CURSORS_OVER_COMMIT = false;

  public static final byte DRIVER_JDBC = (byte)1;

  public static final byte DRIVER_ODBC = (byte)2;

  public static final int DEFAULT_SESSION_TOKEN_SIZE = 16;

  public static final int DEFAULT_RESULTSET_BATCHSIZE = 1024;

  public static final int DEFAULT_LOB_CHUNKSIZE = 1048576;

  public static final byte TRANSACTION_NONE = (byte)0;

  public static final byte TRANSACTION_READ_UNCOMMITTED = (byte)1;

  public static final byte TRANSACTION_READ_COMMITTED = (byte)2;

  public static final byte TRANSACTION_REPEATABLE_READ = (byte)4;

  public static final byte TRANSACTION_SERIALIZABLE = (byte)8;

  public static final byte TRANSACTION_NO_CHANGE = (byte)64;

  public static final byte DEFAULT_TRANSACTION_ISOLATION = (byte)0;

  public static final short COLUMN_UPDATABLE = (short)1;

  public static final short COLUMN_DEFINITELY_UPDATABLE = (short)2;

  public static final short COLUMN_NONULLS = (short)4;

  public static final short COLUMN_NULLABLE = (short)8;

  public static final short COLUMN_AUTOINC = (short)16;

  public static final short PARAMETER_MODE_IN = (short)32;

  public static final short PARAMETER_MODE_INOUT = (short)64;

  public static final short PARAMETER_MODE_OUT = (short)128;

  public static final short COLUMN_PRECISION_UNKNOWN = (short)0;

  public static final short COLUMN_SCALE_UNKNOWN = (short)0;

  public static final int DECIMAL_MAX_PRECISION = 127;

  public static final byte CURSOR_UPDATE = (byte)1;

  public static final byte CURSOR_INSERT = (byte)2;

  public static final byte CURSOR_DELETE = (byte)3;

  public static final int INVALID_ID = 0;

  public static final byte NEXTRS_CLOSE_ALL_RESULTS = (byte)0;

  public static final byte NEXTRS_KEEP_CURRENT_RESULT = (byte)1;

  public static final byte NEXTRS_CLOSE_CURRENT_RESULT = (byte)2;

  public static final byte ROWSET_LAST_BATCH = (byte)1;

  public static final byte ROWSET_HAS_MORE_ROWSETS = (byte)2;

  public static final byte ROWSET_DONE_FOR_LOBS = (byte)4;

  public static final byte BULK_CLOSE_RESULTSET = (byte)1;

  public static final byte BULK_CLOSE_LOB = (byte)2;

  public static final byte BULK_CLOSE_STATEMENT = (byte)3;

  public static final byte BULK_CLOSE_CONNECTION = (byte)4;

}

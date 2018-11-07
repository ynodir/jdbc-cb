
/*
 * //  Copyright (c) 2015 Couchbase, Inc.
 * //  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * //  except in compliance with the License. You may obtain a copy of the License at
 * //    http://www.apache.org/licenses/LICENSE-2.0
 * //  Unless required by applicable law or agreed to in writing, software distributed under the
 * //  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * //  either express or implied. See the License for the specific language governing permissions
 * //  and limitations under the License.
 */

package com.couchbase.jdbc;

import com.couchbase.jdbc.core.*;
import com.couchbase.jdbc.util.TimestampUtils;
import com.couchbase.json.SQLJSON;
import org.boon.json.JsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by davec on 2015-02-20.
 */
public class CBResultSet implements java.sql.ResultSet
{
    private final static Logger logger = LoggerFactory.getLogger(CBResultSet.class);

    final CouchResponse response;

    AtomicBoolean closed = new AtomicBoolean(false);

    Statement statement;
    boolean wasNull = false;
    boolean isMissing = false;

    int index=-1;
    List <Field> fields = new ArrayList<Field>();

    private final TimestampUtils timestampUtils = new TimestampUtils();

    public CBResultSet(Statement statement, CouchResponse response)
    {

        this.statement = statement;
        this.response = response;

        if ( response.getMetrics().getResultSize()  == 0 )
        {
            return;
        }


        fields = response.getFields();
        logger.debug("Loaded result set");
    }
    /**
     * Moves the cursor forward one row from its current position.
     * A <code>ResultSet</code> cursor is initially positioned
     * before the first row; the first call to the method
     * <code>next</code> makes the first row the current row; the
     * second call makes the second row the current row, and so on.
     * 
     * When a call to the <code>next</code> method returns <code>false</code>,
     * the cursor is positioned after the last row. Any
     * invocation of a <code>ResultSet</code> method which requires a
     * current row will result in a <code>SQLException</code> being thrown.
     * If the result set type is <code>TYPE_FORWARD_ONLY</code>, it is vendor specified
     * whether their JDBC driver implementation will return <code>false</code> or
     * throw an <code>SQLException</code> on a
     * subsequent call to <code>next</code>.
     * 
     * <P>If an input stream is open for the current row, a call
     * to the method <code>next</code> will
     * implicitly close it. A <code>ResultSet</code> object's
     * warning chain is cleared when a new row is read.
     *
     * @return <code>true</code> if the new current row is valid;
     * <code>false</code> if there are no more rows
     * @throws java.sql.SQLException if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public boolean next() throws SQLException
    {
        checkClosed();
        index++;
        return (index < response.getResults().size() );

    }

    /**
     * Releases this <code>ResultSet</code> object's database and
     * JDBC resources immediately instead of waiting for
     * this to happen when it is automatically closed.
     * 
     * <P>The closing of a <code>ResultSet</code> object does <strong>not</strong> close the <code>Blob</code>,
     * <code>Clob</code> or <code>NClob</code> objects created by the <code>ResultSet</code>. <code>Blob</code>,
     * <code>Clob</code> or <code>NClob</code> objects remain valid for at least the duration of the
     * transaction in which they are created, unless their <code>free</code> method is invoked.
     * 
     * When a <code>ResultSet</code> is closed, any <code>ResultSetMetaData</code>
     * instances that were created by calling the  <code>getMetaData</code>
     * method remain accessible.
     * 
     * <P><B>Note:</B> A <code>ResultSet</code> object
     * is automatically closed by the
     * <code>Statement</code> object that generated it when
     * that <code>Statement</code> object is closed,
     * re-executed, or is used to retrieve the next result from a
     * sequence of multiple results.
     * 
     * Calling the method <code>close</code> on a <code>ResultSet</code>
     * object that is already closed is a no-op.
     * 
     * 
     *
     * @throws java.sql.SQLException if a database access error occurs
     */
    @Override
    public void close() throws SQLException
    {
        closed.set(true);

    }

    /**
     * Reports whether
     * the last column read had a value of SQL <code>NULL</code>.
     * Note that you must first call one of the getter methods
     * on a column to try to read its value and then call
     * the method <code>wasNull</code> to see if the value read was
     * SQL <code>NULL</code>.
     *
     * @return <code>true</code> if the last column value read was SQL
     * <code>NULL</code> and <code>false</code> otherwise
     * @throws java.sql.SQLException if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public boolean wasNull() throws SQLException
    {
        checkClosed();
        checkIndex();
        return wasNull;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>String</code> in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public String getString(int columnIndex) throws SQLException
    {

        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);
        return getStringChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>boolean</code> in the Java programming language.
     * 
     * <P>If the designated column has a datatype of CHAR or VARCHAR
     * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER or BIGINT
     * and contains  a 0, a value of <code>false</code> is returned.  If the designated column has a datatype
     * of CHAR or VARCHAR
     * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER or BIGINT
     * and contains  a 1, a value of <code>true</code> is returned.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>false</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public boolean getBoolean(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        Field field = getField(columnIndex);

        return getBooleanChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>byte</code> in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public byte getByte(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        Field field  = getField(columnIndex);
        return getByteChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>short</code> in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public short getShort(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        //now find the key of the first value
        Field field  = getField(columnIndex);
        return getShortChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * an <code>int</code> in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public int getInt(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();


        //now find the key of the first value
        Field field  = getField(columnIndex);

        return getIntChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>long</code> in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public long getLong(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);
        return getLongChecked( field.getName() );
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>float</code> in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public float getFloat(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);

        return getFloatChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>double</code> in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public double getDouble(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);

        return getDoubleChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>java.sql.BigDecimal</code> in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param scale       the number of digits to the right of the decimal point
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException           if the columnIndex is not valid;
     *                                         if a database access error occurs or this method is
     *                                         called on a closed result set
     * @throws SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                         this method
     * @deprecated
     */
    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException
    {
        checkClosed();
        checkIndex();

        Field field  = getField(columnIndex);

        return getBigDecimalChecked(field.getName(),scale);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>byte</code> array in the Java programming language.
     * The bytes represent the raw values returned by the driver.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public byte[] getBytes(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        //now find the key of the first value
        Field field  = getField(columnIndex);
        return getBytesChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>java.sql.Date</code> object in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public Date getDate(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);

        return getDateChecked(field.getName(),null);

    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>java.sql.Time</code> object in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public Time getTime(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        Field field = getField(columnIndex);
        return getTimeChecked(field.getName(),null);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>java.sql.Timestamp</code> object in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);
        return getTimestampChecked(field.getName(),null);

    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a stream of ASCII characters. The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARCHAR</code> values.
     * The JDBC driver will
     * do any necessary conversion from the database format into ASCII.
     * 
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a getter method implicitly closes the stream.  Also, a
     * stream may return <code>0</code> when the method
     * <code>InputStream.available</code>
     * is called whether there is data available or not.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a Java input stream that delivers the database column value
     * as a stream of one-byte ASCII characters;
     * if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);
        return getAsciiStreamChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * as a stream of two-byte 3 characters. The first byte is
     * the high byte; the second byte is the low byte.
     * 
     * The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARCHAR</code>values.  The
     * JDBC driver will do any necessary conversion from the database
     * format into Unicode.
     * 
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a getter method implicitly closes the stream.
     * Also, a stream may return <code>0</code> when the method
     * <code>InputStream.available</code>
     * is called, whether there is data available or not.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a Java input stream that delivers the database column value
     * as a stream of two-byte Unicode characters;
     * if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code>
     * @throws java.sql.SQLException           if the columnIndex is not valid;
     *                                         if a database access error occurs or this method is
     *                                         called on a closed result set
     * @throws SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                         this method
     * @deprecated use <code>getCharacterStream</code> in place of
     * <code>getUnicodeStream</code>
     */
    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);
        return getUnicodeStreamChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a  stream of
     * uninterpreted bytes. The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARBINARY</code> values.
     * 
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a getter method implicitly closes the stream.  Also, a
     * stream may return <code>0</code> when the method
     * <code>InputStream.available</code>
     * is called whether there is data available or not.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a Java input stream that delivers the database column value
     * as a stream of uninterpreted bytes;
     * if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException
    {

        checkClosed();
        checkIndex();
        Field field  = getField(columnIndex);
        return getBinaryStreamChecked(field.getName());


    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>String</code> in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public String getString(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();

        return getStringChecked(columnLabel);

    }
    // internal to avoid checking twice
    private String getStringChecked(String columnLabel) throws SQLException
    {
        Map <String,Object>jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }

        Object object = jsonObject.get(columnLabel);

        if (wasNull = (object == null))
        {
            return null;
        }

        if (object instanceof  Map)
        {
            return JsonFactory.toJson(object);
        }
        else
        {

            if (!(object  instanceof String))
            {
                return object.toString();
            }
            else
                return (String)object;
        }
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>boolean</code> in the Java programming language.
     * 
     * <P>If the designated column has a datatype of CHAR or VARCHAR
     * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER or BIGINT
     * and contains  a 0, a value of <code>false</code> is returned.  If the designated column has a datatype
     * of CHAR or VARCHAR
     * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER or BIGINT
     * and contains  a 1, a value of <code>true</code> is returned.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>false</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public boolean getBoolean(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getBooleanChecked(columnLabel);
    }

    private boolean getBooleanChecked(String columnLabel) throws SQLException
    {
        Map <String,Object> jsonObject = response.getResults().get(index);
        checkColumnLabelMissing(jsonObject, columnLabel);

        Object object = jsonObject.get(columnLabel);

        if ( wasNull = (object == null) )
        {
            return false;
        }

        return  (boolean) object;

    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>byte</code> in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public byte getByte(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getByteChecked(columnLabel);
    }

    private byte getByteChecked(String columnLabel) throws SQLException
    {
        byte value;
        Map <String,Object> jsonObject = response.getResults().get(index);
        checkColumnLabelMissing(jsonObject, columnLabel);
        try
        {
            Object object = jsonObject.get(columnLabel);
            if ( wasNull = (object == null) )
            {
                value=0;
            }
            else if ( object instanceof  Integer )
            {
                value =  ((Integer)object).byteValue();
            }
            else
            {
                value = Byte.parseByte(object.toString());

            }
        }

        catch( Exception ex)
        {
            throw new SQLException("value is not a byte");
        }

        logger.info("value {}", value);
        return value;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>short</code> in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public short getShort(String columnLabel) throws SQLException
    {

        checkClosed();
        checkIndex();
        return getShortChecked(columnLabel);
    }

    private short getShortChecked(String columnLabel) throws SQLException
    {
        short value;

        Map <String,Object> jsonObject = response.getResults().get(index);
        checkColumnLabelMissing(jsonObject, columnLabel);

        try
        {
            Object object = jsonObject.get(columnLabel);

            if ( wasNull = (object == null) )
            {
                value = 0;
            }
            else if (object instanceof Integer)
            {
                value = ((Integer) object).shortValue();
            }
            else
            {
                value = Short.parseShort(object.toString());
            }
        }
        catch( Exception ex)
        {
            throw new SQLException("value is not a short");
        }

        logger.info("value {}", value);
        return value;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * an <code>int</code> in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public int getInt(String columnLabel) throws SQLException
    {

        checkClosed();
        checkIndex();
        return getIntChecked(columnLabel);

    }
    private int getIntChecked(String columnLabel) throws SQLException
    {
        int value;
        Map <String,Object> jsonObject = response.getResults().get(index);

       if( checkColumnLabelMissing(jsonObject, columnLabel))
       {
           return 0;
       }


        try
        {
            Object object = jsonObject.get(columnLabel);

            if ( wasNull = (object == null) )
            {
                value =0;
            }
            else if ( object instanceof  Number )
            {
                value = ((Number)object).intValue();
            }
            else if (object instanceof String)
            {
                value = Integer.parseInt((String)object);
            }
            else
            {
                throw new SQLException("value {} is not an integer", object.toString());
            }
        }
        catch( NumberFormatException ex)
        {
            throw new SQLException("value is not an integer");
        }

        logger.info("value {}", jsonObject.get(columnLabel));
        return value;
    }
    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>long</code> in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public long getLong(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getLongChecked(columnLabel);
    }

    private long getLongChecked(String columnLabel) throws SQLException
    {
        long value = 0;
        Map <String,Object> jsonObject = response.getResults().get(index);
        checkColumnLabelMissing(jsonObject, columnLabel);


        try
        {
            Object object = jsonObject.get(columnLabel);

            if ( object == null )
            {
                wasNull=true;
            }
            else if ( object instanceof  Number )
            {
                value = ((Number)object).intValue();
            }
            else if (object instanceof String)
            {
                value = Long.parseLong((String) object);
            }
            else
            {
                throw new SQLException("value " + object.toString() + " is not a long");
            }
        }
        catch( NumberFormatException ex)
        {
            throw new SQLException("value is not a long");
        }

        logger.info("value {}", jsonObject.get(columnLabel));
        return value;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>float</code> in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public float getFloat(String columnLabel) throws SQLException
    {

        checkClosed();
        checkIndex();
        return getFloatChecked(columnLabel);
    }

    private float getFloatChecked(String columnLabel) throws SQLException
    {
        float value=0;
        Map <String,Object> jsonObject = response.getResults().get(index);
        checkColumnLabelMissing(jsonObject, columnLabel);


        try
        {
            Object object = jsonObject.get(columnLabel);

            if ( object == null )
            {
                wasNull=true;
            }
            else if ( object instanceof  Number )
            {
                value = ((Number)object).floatValue();
            }
            else if (object instanceof String)
            {
                value = Float.parseFloat((String) object);
            }
            else
            {
                throw new SQLException("value {} is not a float", object.toString());
            }
        }
        catch( NumberFormatException ex)
        {
            throw new SQLException("value is not a float");
        }

        logger.info("value {}", jsonObject.get(columnLabel));
        return value;


    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>double</code> in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public double getDouble(String columnLabel) throws SQLException
    {

        checkClosed();
        checkIndex();

        return getDoubleChecked(columnLabel);
    }

    private double getDoubleChecked(String columnLabel) throws SQLException
    {
        double value=0;

        Map <String,Object> jsonObject = response.getResults().get(index);
        checkColumnLabelMissing(jsonObject, columnLabel);


        try
        {
            Object object = jsonObject.get(columnLabel);

            if ( object == null )
            {
                wasNull=true;
            }
            else if ( object instanceof  Number )
            {
                value = ((Number)object).doubleValue();
            }
            else if (object instanceof String)
            {
                value = Double.parseDouble((String) object);
            }
            else
            {
                throw new SQLException("value {} is not a double", object.toString());
            }
        }
        catch( NumberFormatException ex)
        {
            throw new SQLException("value is not a double");
        }

        return value;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>java.math.BigDecimal</code> in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param scale       the number of digits to the right of the decimal point
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException           if the columnLabel is not valid;
     *                                         if a database access error occurs or this method is
     *                                         called on a closed result set
     * @throws SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                         this method
     * @deprecated
     */
    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException
    {

        checkClosed();
        checkIndex();
        return getBigDecimalChecked(columnLabel, scale);

    }

    private BigDecimal getBigDecimalChecked(String columnLabel, int scale) throws SQLException
    {
        BigDecimal value;
        Map <String,Object> jsonObject = response.getResults().get(index);
        checkColumnLabelMissing(jsonObject, columnLabel);

        Object json = jsonObject.get(columnLabel);

        if ( wasNull = (json == null) )
        {
            return null;
        }

        try
        {
            value = new BigDecimal((double)json);
            value = value.setScale(scale,BigDecimal.ROUND_HALF_UP);
        }
        catch( Exception ex)
        {
            throw new SQLException("value is not a Big Decimal");
        }

        logger.info("value {}", value);
        return value;

    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>byte</code> array in the Java programming language.
     * The bytes represent the raw values returned by the driver.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public byte[] getBytes(String columnLabel) throws SQLException
    {

        checkClosed();
        checkIndex();
        return getBytesChecked(columnLabel);
    }

    private byte[] getBytesChecked(String columnLabel) throws SQLException
    {
        Map <String,Object> jsonObject = response.getResults().get(index);
        checkColumnLabelMissing(jsonObject, columnLabel);

        String json = (String)jsonObject.get(columnLabel);

        if ( wasNull = (json == null) )
        {
            return null;
        }


        try
        {
            return json.getBytes();

        }
        catch( Exception ex)
        {
            throw new SQLException("Error getting bytes ", ex);
        }
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>java.sql.Date</code> object in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd") ;
    @Override
    public Date getDate(String columnLabel) throws SQLException
    {
       return getDate(columnLabel,null);
    }

    SimpleDateFormat tf = new SimpleDateFormat("HH:mm:ss");
    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>java.sql.Time</code> object in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public Time getTime(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getTimeChecked(columnLabel, null);
    }

    SimpleDateFormat tsf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>java.sql.Timestamp</code> object in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getTimestampChecked(columnLabel, null);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a stream of
     * ASCII characters. The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARCHAR</code> values.
     * The JDBC driver will
     * do any necessary conversion from the database format into ASCII.
     * 
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a getter method implicitly closes the stream. Also, a
     * stream may return <code>0</code> when the method <code>available</code>
     * is called whether there is data available or not.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a Java input stream that delivers the database column value
     * as a stream of one-byte ASCII characters.
     * If the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code>.
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getAsciiStreamChecked(columnLabel);
    }

    private InputStream getAsciiStreamChecked(String columnLabel) throws SQLException
    {
        ByteArrayInputStream byteArrayInputStream;

        Map <String,Object> jsonObject = response.getResults().get(index);

        String json;

        checkColumnLabelMissing(jsonObject, columnLabel);

        json = (String)jsonObject.get(columnLabel);
        if ( wasNull = (json == null) )
        {
            return null;
        }
        try
        {
            //noinspection ConstantConditions
            byteArrayInputStream = new ByteArrayInputStream(getStringChecked(columnLabel).getBytes("ASCII"));
        }
        catch( UnsupportedEncodingException ex)
        {
            throw new SQLException("data is not ASCII");
        }

        return byteArrayInputStream;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a stream of two-byte
     * Unicode characters. The first byte is the high byte; the second
     * byte is the low byte.
     * 
     * The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARCHAR</code> values.
     * The JDBC technology-enabled driver will
     * do any necessary conversion from the database format into Unicode.
     * 
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a getter method implicitly closes the stream.
     * Also, a stream may return <code>0</code> when the method
     * <code>InputStream.available</code> is called, whether there
     * is data available or not.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a Java input stream that delivers the database column value
     * as a stream of two-byte Unicode characters.
     * If the value is SQL <code>NULL</code>, the value returned
     * is <code>null</code>.
     * @throws java.sql.SQLException           if the columnLabel is not valid;
     *                                         if a database access error occurs or this method is
     *                                         called on a closed result set
     * @throws SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                         this method
     * @deprecated use <code>getCharacterStream</code> instead
     */
    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getUnicodeStreamChecked(columnLabel);
    }
    private InputStream getUnicodeStreamChecked(String columnLabel) throws SQLException
    {

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
            return null;

        Object json = jsonObject.get(columnLabel);
        if ( wasNull = (json == null) )
        {
            return null;
        }

        try
        {
            //noinspection ConstantConditions
            return new ByteArrayInputStream(getStringChecked(columnLabel).getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException l_uee)
        {
            throw new SQLException("The JVM claims not to support the encoding: {0}","UTF-8", l_uee);
        }
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a stream of uninterpreted
     * <code>byte</code>s.
     * The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARBINARY</code>
     * values.
     * 
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a getter method implicitly closes the stream. Also, a
     * stream may return <code>0</code> when the method <code>available</code>
     * is called whether there is data available or not.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a Java input stream that delivers the database column value
     * as a stream of uninterpreted bytes;
     * if the value is SQL <code>NULL</code>, the result is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException
    {

        checkClosed();
        checkIndex();
        return getBinaryStreamChecked(columnLabel);
    }
    private InputStream getBinaryStreamChecked(String columnLabel) throws SQLException
    {

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }
        Object json = jsonObject.get(columnLabel);
        if ( wasNull = (json == null) )
        {
            return null;
        }

        //noinspection ConstantConditions
        return new ByteArrayInputStream(getStringChecked(columnLabel).getBytes());


    }


    /**
     * Retrieves the first warning reported by calls on this
     * <code>ResultSet</code> object.
     * Subsequent warnings on this <code>ResultSet</code> object
     * will be chained to the <code>SQLWarning</code> object that
     * this method returns.
     * 
     * <P>The warning chain is automatically cleared each time a new
     * row is read.  This method may not be called on a <code>ResultSet</code>
     * object that has been closed; doing so will cause an
     * <code>SQLException</code> to be thrown.
     * 
     * <B>Note:</B> This warning chain only covers warnings caused
     * by <code>ResultSet</code> methods.  Any warning caused by
     * <code>Statement</code> methods
     * (such as reading OUT parameters) will be chained on the
     * <code>Statement</code> object.
     *
     * @return the first <code>SQLWarning</code> object reported or
     * <code>null</code> if there are none
     * @throws java.sql.SQLException if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public SQLWarning getWarnings() throws SQLException
    {
        //noinspection ConstantConditions
        if ( response == null ) throw new SQLException("Result Set is closed");

        SQLWarning sqlWarning=null;

        for (CouchError warning : response.getWarnings())
        {
            if ( sqlWarning != null )
            {
                sqlWarning = new SQLWarning(warning.getMsg(),null, warning.getCode());
            }
            else
            {
                sqlWarning.setNextWarning(new SQLWarning(warning.getMsg(),null, warning.getCode()));
            }
        }
        return sqlWarning;
    }

    /**
     * Clears all warnings reported on this <code>ResultSet</code> object.
     * After this method is called, the method <code>getWarnings</code>
     * returns <code>null</code> until a new warning is
     * reported for this <code>ResultSet</code> object.
     *
     * @throws java.sql.SQLException if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public void clearWarnings() throws SQLException
    {
        checkClosed();
    }

    /**
     * Retrieves the name of the SQL cursor used by this <code>ResultSet</code>
     * object.
     * 
     * <P>In SQL, a result table is retrieved through a cursor that is
     * named. The current row of a result set can be updated or deleted
     * using a positioned update/delete statement that references the
     * cursor name. To insure that the cursor has the proper isolation
     * level to support update, the cursor's <code>SELECT</code> statement
     * should be of the form <code>SELECT FOR UPDATE</code>. If
     * <code>FOR UPDATE</code> is omitted, the positioned updates may fail.
     * 
     * <P>The JDBC API supports this SQL feature by providing the name of the
     * SQL cursor used by a <code>ResultSet</code> object.
     * The current row of a <code>ResultSet</code> object
     * is also the current row of this SQL cursor.
     *
     * @return the SQL name for this <code>ResultSet</code> object's cursor
     * @throws java.sql.SQLException                    if a database access error occurs or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     */
    @Override
    public String getCursorName() throws SQLException
    {
        throw CBDriver.notImplemented(this.getClass(), "getCursorName");
    }

    /**
     * Retrieves the  number, types and properties of
     * this <code>ResultSet</code> object's columns.
     *
     * @return the description of this <code>ResultSet</code> object's columns
     * @throws java.sql.SQLException if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public ResultSetMetaData getMetaData() throws SQLException
    {
        return new CBResultSetMetaData(this);
    }

    /**
     * <p>Gets the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * an <code>Object</code> in the Java programming language.
     * 
     * <p>This method will return the value of the given column as a
     * Java object.  The type of the Java object will be the default
     * Java object type corresponding to the column's SQL type,
     * following the mapping for built-in types specified in the JDBC
     * specification. If the value is an SQL <code>NULL</code>,
     * the driver returns a Java <code>null</code>.
     * 
     * <p>This method may also be used to read database-specific
     * abstract data types.
     * 
     * In the JDBC 2.0 API, the behavior of method
     * <code>getObject</code> is extended to materialize
     * data of SQL user-defined types.
     * 
     * If <code>Connection.getTypeMap</code> does not throw a
     * <code>SQLFeatureNotSupportedException</code>,
     * then when a column contains a structured or distinct value,
     * the behavior of this method is as
     * if it were a call to: <code>getObject(columnIndex,
     * this.getStatement().getConnection().getTypeMap())</code>.
     * 
     * If <code>Connection.getTypeMap</code> does throw a
     * <code>SQLFeatureNotSupportedException</code>,
     * then structured values are not supported, and distinct values
     * are mapped to the default Java class as determined by the
     * underlying SQL type of the DISTINCT type.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>java.lang.Object</code> holding the column value
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public Object getObject(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        Field field = getField(columnIndex);
        String fieldName = field.getName();

        return getObjectChecked(fieldName);
    }

    /**
     * <p>Gets the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * an <code>Object</code> in the Java programming language.
     * 
     * <p>This method will return the value of the given column as a
     * Java object.  The type of the Java object will be the default
     * Java object type corresponding to the column's SQL type,
     * following the mapping for built-in types specified in the JDBC
     * specification. If the value is an SQL <code>NULL</code>,
     * the driver returns a Java <code>null</code>.
     * 
     * This method may also be used to read database-specific
     * abstract data types.
     * 
     * In the JDBC 2.0 API, the behavior of the method
     * <code>getObject</code> is extended to materialize
     * data of SQL user-defined types.  When a column contains
     * a structured or distinct value, the behavior of this method is as
     * if it were a call to: <code>getObject(columnIndex,
     * this.getStatement().getConnection().getTypeMap())</code>.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a <code>java.lang.Object</code> holding the column value
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     */
    @Override
    public Object getObject(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getObjectChecked(columnLabel);
    }

    private Object getObjectChecked(String columnLabel) throws SQLException
    {

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }

        Object object = jsonObject.get(columnLabel);
        if ( wasNull = (object == null) )
        {
            return null;
        }

        for (Field field:fields)
        {
            if (field.getName().equals(columnLabel))
            {
                switch (field.getSqlType())
                {
                    case Types.NUMERIC:
                    case Types.BOOLEAN:
                        return object;

                    case Types.VARCHAR:
                        if (object instanceof java.util.Date) {
                            return new java.sql.Date(((java.util.Date)object).getTime());   //TODO this might fail also
                        } else {
                            return object;
                        }
                    case Types.ARRAY:
                    case Types.JAVA_OBJECT:
                        return object;

                    case Types.NULL:
                        return null;
                }
            }
        }


        return null;
    }

    public SQLJSON getSQLJSON( int columnIndex ) throws SQLException
    {
        checkClosed();
        checkIndex();
        //now find the key of the first value
        Field field  = getField(columnIndex);

        return getSQLJSONChecked(field.getName());
    }
    public SQLJSON getSQLJSON(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getSQLJSONChecked(columnLabel);
    }
    private SQLJSON getSQLJSONChecked(String columnLabel) throws SQLException
    {
        Map <String,Object> jsonObject = response.getResults().get(index);
        if( checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }

        Object object = jsonObject.get(columnLabel);
        if ( wasNull = (object == null) )
        {
           return null;
        }
        int columnIndex = findColumn(columnLabel)-1;

        return new SqlJsonImplementation( object, fields.get(columnIndex));
    }
    /**
     * Maps the given <code>ResultSet</code> column label to its
     * <code>ResultSet</code> column index.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column index of the given column name
     * @throws java.sql.SQLException if the <code>ResultSet</code> object
     *                               does not contain a column labeled <code>columnLabel</code>, a database access error occurs
     *                               or this method is called on a closed result set
     */
    @Override
    public int findColumn(String columnLabel) throws SQLException
    {

        for (int i=0; i < fields.size();i++)
        {

            if (fields.get(i).getName().equals(columnLabel) )
            {
                return i+1;
            }
        }
        throw new SQLException("column " + columnLabel + " does not exist");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a
     * <code>java.io.Reader</code> object.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>java.io.Reader</code> object that contains the column
     * value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language.
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     * @since 1.2
     */
    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        Field field  = getField(columnIndex);

        return getCharacterStreamChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a
     * <code>java.io.Reader</code> object.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a <code>java.io.Reader</code> object that contains the column
     * value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     * @since 1.2
     */
    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getCharacterStreamChecked(columnLabel);
    }
    private Reader getCharacterStreamChecked(String columnLabel) throws SQLException
    {
        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }
        Object object = jsonObject.get(columnLabel);
        if (wasNull = (object == null ))
        {
            return null;
        }
        return new CharArrayReader(getStringChecked(columnLabel).toCharArray());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a
     * <code>java.math.BigDecimal</code> with full precision.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value (full precision);
     * if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language.
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     * @since 1.2
     */
    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        Field field  = getField(columnIndex);
        return getBigDecimalChecked(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a
     * <code>java.math.BigDecimal</code> with full precision.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value (full precision);
     * if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language.
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs or this method is
     *                               called on a closed result set
     * @since 1.2
     */
    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getBigDecimalChecked(columnLabel);
    }

    private BigDecimal getBigDecimalChecked(String columnLabel) throws SQLException
    {
        BigDecimal value=null;

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }

        try
        {
            Object object =  jsonObject.get(columnLabel);
            if ( wasNull = (object == null) )
            {
                return null;
            }

            else if( object instanceof Number )
            {
                value = new BigDecimal(((Number) object).doubleValue());
            }
            else if (object instanceof BigDecimal)
            {
                value = (BigDecimal) object;
            }
        }
        catch( Exception ex)
        {
            throw new SQLException("value is not a Big Decimal");
        }

        logger.info("value {}", value);
        return value;
    }

    /**
     * Retrieves whether the cursor is before the first row in
     * this <code>ResultSet</code> object.
     * 
     * <strong>Note:</strong>Support for the <code>isBeforeFirst</code> method
     * is optional for <code>ResultSet</code>s with a result
     * set type of <code>TYPE_FORWARD_ONLY</code>
     *
     * @return <code>true</code> if the cursor is before the first row;
     * <code>false</code> if the cursor is at any other position or the
     * result set contains no rows
     * @throws java.sql.SQLException                    if a database access error occurs or this method is
     *                                                  called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean isBeforeFirst() throws SQLException
    {
        throw CBDriver.notImplemented( CBResultSet.class, "isBeforeFirst" );
    }

    /**
     * Retrieves whether the cursor is after the last row in
     * this <code>ResultSet</code> object.
     * 
     * <strong>Note:</strong>Support for the <code>isAfterLast</code> method
     * is optional for <code>ResultSet</code>s with a result
     * set type of <code>TYPE_FORWARD_ONLY</code>
     *
     * @return <code>true</code> if the cursor is after the last row;
     * <code>false</code> if the cursor is at any other position or the
     * result set contains no rows
     * @throws java.sql.SQLException                    if a database access error occurs or this method is
     *                                                  called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean isAfterLast() throws SQLException
    {
        throw CBDriver.notImplemented( CBResultSet.class, "isBeforeFirst" );
    }

    /**
     * Retrieves whether the cursor is on the first row of
     * this <code>ResultSet</code> object.
     * 
     * <strong>Note:</strong>Support for the <code>isFirst</code> method
     * is optional for <code>ResultSet</code>s with a result
     * set type of <code>TYPE_FORWARD_ONLY</code>
     *
     * @return <code>true</code> if the cursor is on the first row;
     * <code>false</code> otherwise
     * @throws java.sql.SQLException                    if a database access error occurs or this method is
     *                                                  called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean isFirst() throws SQLException
    {
        throw CBDriver.notImplemented( CBResultSet.class, "isBeforeFirst" );
    }

    /**
     * Retrieves whether the cursor is on the last row of
     * this <code>ResultSet</code> object.
     * <strong>Note:</strong> Calling the method <code>isLast</code> may be expensive
     * because the JDBC driver
     * might need to fetch ahead one row in order to determine
     * whether the current row is the last row in the result set.
     * 
     * <strong>Note:</strong> Support for the <code>isLast</code> method
     * is optional for <code>ResultSet</code>s with a result
     * set type of <code>TYPE_FORWARD_ONLY</code>
     *
     * @return <code>true</code> if the cursor is on the last row;
     * <code>false</code> otherwise
     * @throws java.sql.SQLException                    if a database access error occurs or this method is
     *                                                  called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean isLast() throws SQLException
    {
        throw CBDriver.notImplemented( CBResultSet.class, "isBeforeFirst" );
    }

    /**
     * Moves the cursor to the front of
     * this <code>ResultSet</code> object, just before the
     * first row. This method has no effect if the result set contains no rows.
     *
     * @throws java.sql.SQLException                    if a database access error
     *                                                  occurs; this method is called on a closed result set or the
     *                                                  result set type is <code>TYPE_FORWARD_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void beforeFirst() throws SQLException
    {
        throw CBDriver.notImplemented( CBResultSet.class, "isBeforeFirst" );
    }

    /**
     * Moves the cursor to the end of
     * this <code>ResultSet</code> object, just after the
     * last row. This method has no effect if the result set contains no rows.
     *
     * @throws java.sql.SQLException                    if a database access error
     *                                                  occurs; this method is called on a closed result set
     *                                                  or the result set type is <code>TYPE_FORWARD_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void afterLast() throws SQLException
    {
        checkClosed();

        index = (int)response.getMetrics().getResultCount();

    }

    /**
     * Moves the cursor to the first row in
     * this <code>ResultSet</code> object.
     *
     * @return <code>true</code> if the cursor is on a valid row;
     * <code>false</code> if there are no rows in the result set
     * @throws java.sql.SQLException                    if a database access error
     *                                                  occurs; this method is called on a closed result set
     *                                                  or the result set type is <code>TYPE_FORWARD_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean first() throws SQLException
    {
        checkClosed();
        index =0;

        // if there are results
        return response.getResults().size() > 0;
    }


    /**
     * Moves the cursor to the last row in
     * this <code>ResultSet</code> object.
     *
     * @return <code>true</code> if the cursor is on a valid row;
     * <code>false</code> if there are no rows in the result set
     * @throws java.sql.SQLException                    if a database access error
     *                                                  occurs; this method is called on a closed result set
     *                                                  or the result set type is <code>TYPE_FORWARD_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean last() throws SQLException
    {
        checkClosed();
        index = response.getResults().size();

        // if there are results
        return response.getResults().size() > 0;
    }

    /**
     * Retrieves the current row number.  The first row is number 1, the
     * second number 2, and so on.
     * 
     * <strong>Note:</strong>Support for the <code>getRow</code> method
     * is optional for <code>ResultSet</code>s with a result
     * set type of <code>TYPE_FORWARD_ONLY</code>
     *
     * @return the current row number; <code>0</code> if there is no current row
     * @throws java.sql.SQLException                    if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public int getRow() throws SQLException
    {
        checkClosed();
        return index + 1;
    }

    /**
     * Moves the cursor to the given row number in
     * this <code>ResultSet</code> object.
     * 
     * <p>If the row number is positive, the cursor moves to
     * the given row number with respect to the
     * beginning of the result set.  The first row is row 1, the second
     * is row 2, and so on.
     * 
     * <p>If the given row number is negative, the cursor moves to
     * an absolute row position with respect to
     * the end of the result set.  For example, calling the method
     * <code>absolute(-1)</code> positions the
     * cursor on the last row; calling the method <code>absolute(-2)</code>
     * moves the cursor to the next-to-last row, and so on.
     * 
     * <p>If the row number specified is zero, the cursor is moved to
     * before the first row.
     * 
     * <p>An attempt to position the cursor beyond the first/last row in
     * the result set leaves the cursor before the first row or after
     * the last row.
     * 
     * <p><B>Note:</B> Calling <code>absolute(1)</code> is the same
     * as calling <code>first()</code>. Calling <code>absolute(-1)</code>
     * is the same as calling <code>last()</code>.
     *
     * @param row the number of the row to which the cursor should move.
     *            A value of zero indicates that the cursor will be positioned
     *            before the first row; a positive number indicates the row number
     *            counting from the beginning of the result set; a negative number
     *            indicates the row number counting from the end of the result set
     * @return <code>true</code> if the cursor is moved to a position in this
     * <code>ResultSet</code> object;
     * <code>false</code> if the cursor is before the first row or after the
     * last row
     * @throws java.sql.SQLException                    if a database access error
     *                                                  occurs; this method is called on a closed result set
     *                                                  or the result set type is <code>TYPE_FORWARD_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean absolute(int row) throws SQLException
    {
        throw  CBDriver.notImplemented(CBResultSet.class, "absolute");
    }

    /**
     * Moves the cursor a relative number of rows, either positive or negative.
     * Attempting to move beyond the first/last row in the
     * result set positions the cursor before/after the
     * the first/last row. Calling <code>relative(0)</code> is valid, but does
     * not change the cursor position.
     * 
     * <p>Note: Calling the method <code>relative(1)</code>
     * is identical to calling the method <code>next()</code> and
     * calling the method <code>relative(-1)</code> is identical
     * to calling the method <code>previous()</code>.
     *
     * @param rows an <code>int</code> specifying the number of rows to
     *             move from the current row; a positive number moves the cursor
     *             forward; a negative number moves the cursor backward
     * @return <code>true</code> if the cursor is on a row;
     * <code>false</code> otherwise
     * @throws java.sql.SQLException                    if a database access error occurs;  this method
     *                                                  is called on a closed result set or the result set type is
     *                                                  <code>TYPE_FORWARD_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean relative(int rows) throws SQLException
    {
        throw  CBDriver.notImplemented(CBResultSet.class, "relative");
    }

    /**
     * Moves the cursor to the previous row in this
     * <code>ResultSet</code> object.
     * 
     * When a call to the <code>previous</code> method returns <code>false</code>,
     * the cursor is positioned before the first row.  Any invocation of a
     * <code>ResultSet</code> method which requires a current row will result in a
     * <code>SQLException</code> being thrown.
     * 
     * If an input stream is open for the current row, a call to the method
     * <code>previous</code> will implicitly close it.  A <code>ResultSet</code>
     * object's warning change is cleared when a new row is read.
     * 
     *
     * @return <code>true</code> if the cursor is now positioned on a valid row;
     * <code>false</code> if the cursor is positioned before the first row
     * @throws java.sql.SQLException                    if a database access error
     *                                                  occurs; this method is called on a closed result set
     *                                                  or the result set type is <code>TYPE_FORWARD_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public boolean previous() throws SQLException
    {
        checkClosed();
        throw new SQLException("Result set is Type Forward only");
    }

    /**
     * Gives a hint as to the direction in which the rows in this
     * <code>ResultSet</code> object will be processed.
     * The initial value is determined by the
     * <code>Statement</code> object
     * that produced this <code>ResultSet</code> object.
     * The fetch direction may be changed at any time.
     *
     * @param direction an <code>int</code> specifying the suggested
     *                  fetch direction; one of <code>ResultSet.FETCH_FORWARD</code>,
     *                  <code>ResultSet.FETCH_REVERSE</code>, or
     *                  <code>ResultSet.FETCH_UNKNOWN</code>
     * @throws java.sql.SQLException if a database access error occurs; this
     *                               method is called on a closed result set or
     *                               the result set type is <code>TYPE_FORWARD_ONLY</code> and the fetch
     *                               direction is not <code>FETCH_FORWARD</code>
     * @see java.sql.Statement#setFetchDirection
     * @see #getFetchDirection
     * @since 1.2
     */
    @Override
    public void setFetchDirection(int direction) throws SQLException
    {
        checkClosed();
        if (ResultSet.FETCH_REVERSE == direction )
        {
            //todo needs test
            throw new SQLException("Cannot change fetch direction");
        }
    }

    /**
     * Retrieves the fetch direction for this
     * <code>ResultSet</code> object.
     *
     * @return the current fetch direction for this <code>ResultSet</code> object
     * @throws java.sql.SQLException if a database access error occurs
     *                               or this method is called on a closed result set
     * @see #setFetchDirection
     * @since 1.2
     */
    @Override
    public int getFetchDirection() throws SQLException
    {
        checkClosed();
        return FETCH_FORWARD;
    }

    /**
     * Gives the JDBC driver a hint as to the number of rows that should
     * be fetched from the database when more rows are needed for this
     * <code>ResultSet</code> object.
     * If the fetch size specified is zero, the JDBC driver
     * ignores the value and is free to make its own best guess as to what
     * the fetch size should be.  The default value is set by the
     * <code>Statement</code> object
     * that created the result set.  The fetch size may be changed at any time.
     *
     * @param rows the number of rows to fetch
     * @throws java.sql.SQLException if a database access error occurs; this method
     *                               is called on a closed result set or the
     *                               condition <code>rows &gt;= 0 </code> is not satisfied
     * @see #getFetchSize
     * @since 1.2
     */
    @Override
    public void setFetchSize(int rows) throws SQLException
    {
        checkClosed();
        // ignore value
    }

    /**
     * Retrieves the fetch size for this
     * <code>ResultSet</code> object.
     *
     * @return the current fetch size for this <code>ResultSet</code> object
     * @throws java.sql.SQLException if a database access error occurs
     *                               or this method is called on a closed result set
     * @see #setFetchSize
     * @since 1.2
     */
    @Override
    public int getFetchSize() throws SQLException
    {
        checkClosed();
        return 0;
    }

    /**
     * Retrieves the type of this <code>ResultSet</code> object.
     * The type is determined by the <code>Statement</code> object
     * that created the result set.
     *
     * @return <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     * <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>,
     * or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @throws java.sql.SQLException if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public int getType() throws SQLException
    {
        checkClosed();
        return TYPE_FORWARD_ONLY;
    }

    /**
     * Retrieves the concurrency mode of this <code>ResultSet</code> object.
     * The concurrency used is determined by the
     * <code>Statement</code> object that created the result set.
     *
     * @return the concurrency type, either
     * <code>ResultSet.CONCUR_READ_ONLY</code>
     * or <code>ResultSet.CONCUR_UPDATABLE</code>
     * @throws java.sql.SQLException if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public int getConcurrency() throws SQLException
    {
        checkClosed();
        return ResultSet.CONCUR_READ_ONLY;
    }

    /**
     * Retrieves whether the current row has been updated.  The value returned
     * depends on whether or not the result set can detect updates.
     * 
     * <strong>Note:</strong> Support for the <code>rowUpdated</code> method is optional with a result set
     * concurrency of <code>CONCUR_READ_ONLY</code>
     *
     * @return <code>true</code> if the current row is detected to
     * have been visibly updated by the owner or another; <code>false</code> otherwise
     * @throws java.sql.SQLException                    if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @see java.sql.DatabaseMetaData#updatesAreDetected
     * @since 1.2
     */
    @Override
    public boolean rowUpdated() throws SQLException
    {
        throw CBDriver.notImplemented(ResultSet.class, "rowUpdated");
    }

    /**
     * Retrieves whether the current row has had an insertion.
     * The value returned depends on whether or not this
     * <code>ResultSet</code> object can detect visible inserts.
     * 
     * <strong>Note:</strong> Support for the <code>rowInserted</code> method is optional with a result set
     * concurrency of <code>CONCUR_READ_ONLY</code>
     *
     * @return <code>true</code> if the current row is detected to
     * have been inserted; <code>false</code> otherwise
     * @throws java.sql.SQLException                    if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @see java.sql.DatabaseMetaData#insertsAreDetected
     * @since 1.2
     */
    @Override
    public boolean rowInserted() throws SQLException
    {
        throw CBDriver.notImplemented(ResultSet.class, "rowInserted");
    }

    /**
     * Retrieves whether a row has been deleted.  A deleted row may leave
     * a visible "hole" in a result set.  This method can be used to
     * detect holes in a result set.  The value returned depends on whether
     * or not this <code>ResultSet</code> object can detect deletions.
     * 
     * <strong>Note:</strong> Support for the <code>rowDeleted</code> method is optional with a result set
     * concurrency of <code>CONCUR_READ_ONLY</code>
     *
     * @return <code>true</code> if the current row is detected to
     * have been deleted by the owner or another; <code>false</code> otherwise
     * @throws java.sql.SQLException                    if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @see java.sql.DatabaseMetaData#deletesAreDetected
     * @since 1.2
     */
    @Override
    public boolean rowDeleted() throws SQLException
    {
        throw CBDriver.notImplemented(ResultSet.class, "rowDeleted");

    }

    /**
     * Updates the designated column with a <code>null</code> value.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code>
     * or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateNull(int columnIndex) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNull");
    }

    /**
     * Updates the designated column with a <code>boolean</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBoolean");
    }

    /**
     * Updates the designated column with a <code>byte</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateByte");
    }

    /**
     * Updates the designated column with a <code>short</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateShort(int columnIndex, short x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateShort");
    }

    /**
     * Updates the designated column with an <code>int</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateInt(int columnIndex, int x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateInt");
    }

    /**
     * Updates the designated column with a <code>long</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateLong(int columnIndex, long x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateLong");
    }

    /**
     * Updates the designated column with a <code>float</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateFloat");
    }

    /**
     * Updates the designated column with a <code>double</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateDouble");
    }

    /**
     * Updates the designated column with a <code>java.math.BigDecimal</code>
     * value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBigDecimal");
    }

    /**
     * Updates the designated column with a <code>String</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateString(int columnIndex, String x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateString");
    }

    /**
     * Updates the designated column with a <code>byte</code> array value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBytes");
    }

    /**
     * Updates the designated column with a <code>java.sql.Date</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateDate");
    }

    /**
     * Updates the designated column with a <code>java.sql.Time</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateTime");
    }

    /**
     * Updates the designated column with a <code>java.sql.Timestamp</code>
     * value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateTimestamp");
    }

    /**
     * Updates the designated column with an ascii stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateAsciiStream");
    }

    /**
     * Updates the designated column with a binary stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBinaryStream");
    }

    /**
     * Updates the designated column with a character stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateCharacterStream");
    }

    /**
     * Updates the designated column with an <code>Object</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * If the second argument is an <code>InputStream</code> then the stream must contain
     * the number of bytes specified by scaleOrLength.  If the second argument is a
     * <code>Reader</code> then the reader must contain the number of characters specified
     * by scaleOrLength. If these conditions are not true the driver will generate a
     * <code>SQLException</code> when the statement is executed.
     *
     * @param columnIndex   the first column is 1, the second is 2, ...
     * @param x             the new column value
     * @param scaleOrLength for an object of <code>java.math.BigDecimal</code> ,
     *                      this is the number of digits after the decimal point. For
     *                      Java Object types <code>InputStream</code> and <code>Reader</code>,
     *                      this is the length
     *                      of the data in the stream or reader.  For all other types,
     *                      this value will be ignored.
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateObject");
    }

    /**
     * Updates the designated column with an <code>Object</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateObject");
    }

    /**
     * Updates the designated column with a <code>null</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateNull(String columnLabel) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNull");
    }

    @Override
    public void updateObject(int columnIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateObject");
    }

    @Override
    public void updateObject(String columnLabel, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateObject");
    }

    @Override
    public void updateObject(int columnIndex, Object x, SQLType targetSqlType) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateObject");
    }

    @Override
    public void updateObject(String columnLabel, Object x, SQLType targetSqlType) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateObject");
    }

    /**
     * Updates the designated column with a <code>boolean</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBoolean");
    }

    /**
     * Updates the designated column with a <code>byte</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateByte");
    }

    /**
     * Updates the designated column with a <code>short</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateShort(String columnLabel, short x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateShort");
    }

    /**
     * Updates the designated column with an <code>int</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateInt(String columnLabel, int x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateInt");
    }

    /**
     * Updates the designated column with a <code>long</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateLong(String columnLabel, long x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateLong");
    }

    /**
     * Updates the designated column with a <code>float </code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateFloat");
    }

    /**
     * Updates the designated column with a <code>double</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateDouble");
    }

    /**
     * Updates the designated column with a <code>java.sql.BigDecimal</code>
     * value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBigDecimal");
    }

    /**
     * Updates the designated column with a <code>String</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateString(String columnLabel, String x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateString");
    }

    /**
     * Updates the designated column with a byte array value.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code>
     * or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBytes");
    }

    /**
     * Updates the designated column with a <code>java.sql.Date</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateDate");
    }

    /**
     * Updates the designated column with a <code>java.sql.Time</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateTime");
    }

    /**
     * Updates the designated column with a <code>java.sql.Timestamp</code>
     * value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateTimestamp");
    }

    /**
     * Updates the designated column with an ascii stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateAsciiStream");
    }

    /**
     * Updates the designated column with a binary stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBinaryStream");
    }

    /**
     * Updates the designated column with a character stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      the <code>java.io.Reader</code> object containing
     *                    the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateCharacterStream");
    }

    /**
     * Updates the designated column with an <code>Object</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * If the second argument is an <code>InputStream</code> then the stream must contain
     * the number of bytes specified by scaleOrLength.  If the second argument is a
     * <code>Reader</code> then the reader must contain the number of characters specified
     * by scaleOrLength. If these conditions are not true the driver will generate a
     * <code>SQLException</code> when the statement is executed.
     *
     * @param columnLabel   the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x             the new column value
     * @param scaleOrLength for an object of <code>java.math.BigDecimal</code> ,
     *                      this is the number of digits after the decimal point. For
     *                      Java Object types <code>InputStream</code> and <code>Reader</code>,
     *                      this is the length
     *                      of the data in the stream or reader.  For all other types,
     *                      this value will be ignored.
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateObject");
    }

    /**
     * Updates the designated column with an <code>Object</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateObject");
    }

    /**
     * Inserts the contents of the insert row into this
     * <code>ResultSet</code> object and into the database.
     * The cursor must be on the insert row when this method is called.
     *
     * @throws java.sql.SQLException                    if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>,
     *                                                  this method is called on a closed result set,
     *                                                  if this method is called when the cursor is not on the insert row,
     *                                                  or if not all of non-nullable columns in
     *                                                  the insert row have been given a non-null value
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void insertRow() throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "insertRow");
    }

    /**
     * Updates the underlying database with the new contents of the
     * current row of this <code>ResultSet</code> object.
     * This method cannot be called when the cursor is on the insert row.
     *
     * @throws java.sql.SQLException                    if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>;
     *                                                  this method is called on a closed result set or
     *                                                  if this method is called when the cursor is on the insert row
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void updateRow() throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateRow");
    }

    /**
     * Deletes the current row from this <code>ResultSet</code> object
     * and from the underlying database.  This method cannot be called when
     * the cursor is on the insert row.
     *
     * @throws java.sql.SQLException                    if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>;
     *                                                  this method is called on a closed result set
     *                                                  or if this method is called when the cursor is on the insert row
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void deleteRow() throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "deleteRow");
    }

    /**
     * Refreshes the current row with its most recent value in
     * the database.  This method cannot be called when
     * the cursor is on the insert row.
     * 
     * <P>The <code>refreshRow</code> method provides a way for an
     * application to
     * explicitly tell the JDBC driver to refetch a row(s) from the
     * database.  An application may want to call <code>refreshRow</code> when
     * caching or prefetching is being done by the JDBC driver to
     * fetch the latest value of a row from the database.  The JDBC driver
     * may actually refresh multiple rows at once if the fetch size is
     * greater than one.
     * 
     * <P> All values are refetched subject to the transaction isolation
     * level and cursor sensitivity.  If <code>refreshRow</code> is called after
     * calling an updater method, but before calling
     * the method <code>updateRow</code>, then the
     * updates made to the row are lost.  Calling the method
     * <code>refreshRow</code> frequently will likely slow performance.
     *
     * @throws java.sql.SQLException                    if a database access error
     *                                                  occurs; this method is called on a closed result set;
     *                                                  the result set type is <code>TYPE_FORWARD_ONLY</code> or if this
     *                                                  method is called when the cursor is on the insert row
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method or this method is not supported for the specified result
     *                                                  set type and result set concurrency.
     * @since 1.2
     */
    @Override
    public void refreshRow() throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "refreshRow");
    }

    /**
     * Cancels the updates made to the current row in this
     * <code>ResultSet</code> object.
     * This method may be called after calling an
     * updater method(s) and before calling
     * the method <code>updateRow</code> to roll back
     * the updates made to a row.  If no updates have been made or
     * <code>updateRow</code> has already been called, this method has no
     * effect.
     *
     * @throws java.sql.SQLException                    if a database access error
     *                                                  occurs; this method is called on a closed result set;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or if this method is called when the cursor is
     *                                                  on the insert row
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void cancelRowUpdates() throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "cancelRowUpdates");
    }

    /**
     * Moves the cursor to the insert row.  The current cursor position is
     * remembered while the cursor is positioned on the insert row.
     * 
     * The insert row is a special row associated with an updatable
     * result set.  It is essentially a buffer where a new row may
     * be constructed by calling the updater methods prior to
     * inserting the row into the result set.
     * 
     * Only the updater, getter,
     * and <code>insertRow</code> methods may be
     * called when the cursor is on the insert row.  All of the columns in
     * a result set must be given a value each time this method is
     * called before calling <code>insertRow</code>.
     * An updater method must be called before a
     * getter method can be called on a column value.
     *
     * @throws java.sql.SQLException                    if a database access error occurs; this
     *                                                  method is called on a closed result set
     *                                                  or the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void moveToInsertRow() throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "moveToInsertRow");
    }

    /**
     * Moves the cursor to the remembered cursor position, usually the
     * current row.  This method has no effect if the cursor is not on
     * the insert row.
     *
     * @throws java.sql.SQLException                    if a database access error occurs; this
     *                                                  method is called on a closed result set
     *                                                  or the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public void moveToCurrentRow() throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "moveToCurrentRow");
    }

    /**
     * Retrieves the <code>Statement</code> object that produced this
     * <code>ResultSet</code> object.
     * If the result set was generated some other way, such as by a
     * <code>DatabaseMetaData</code> method, this method  may return
     * <code>null</code>.
     *
     * @return the <code>Statment</code> object that produced
     * this <code>ResultSet</code> object or <code>null</code>
     * if the result set was produced some other way
     * @throws java.sql.SQLException if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public Statement getStatement() throws SQLException
    {
        checkClosed();
        return statement;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as an <code>Object</code>
     * in the Java programming language.
     * If the value is an SQL <code>NULL</code>,
     * the driver returns a Java <code>null</code>.
     * This method uses the given <code>Map</code> object
     * for the custom mapping of the
     * SQL structured or distinct type that is being retrieved.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param map         a <code>java.util.Map</code> object that contains the mapping
     *                    from SQL type names to classes in the Java programming language
     * @return an <code>Object</code> in the Java programming language
     * representing the SQL value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException
    {
        //todo this can be implemented
        return null;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>Ref</code> object
     * in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>Ref</code> object representing an SQL <code>REF</code>
     * value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Ref getRef(int columnIndex) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "getRef");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>Blob</code> object
     * in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>Blob</code> object representing the SQL
     * <code>BLOB</code> value in the specified column
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Blob getBlob(int columnIndex) throws SQLException
    {
        throw CBDriver.notImplemented(ResultSet.class, "getBlob");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>Clob</code> object
     * in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>Clob</code> object representing the SQL
     * <code>CLOB</code> value in the specified column
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Clob getClob(int columnIndex) throws SQLException
    {
        throw CBDriver.notImplemented(ResultSet.class, "getClob");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as an <code>Array</code> object
     * in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return an <code>Array</code> object representing the SQL
     * <code>ARRAY</code> value in the specified column
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Array getArray(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        Field field = getField(columnIndex);
        String fieldName = field.getName();
        return getArrayChecked(fieldName);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as an <code>Object</code>
     * in the Java programming language.
     * If the value is an SQL <code>NULL</code>,
     * the driver returns a Java <code>null</code>.
     * This method uses the specified <code>Map</code> object for
     * custom mapping if appropriate.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param map         a <code>java.util.Map</code> object that contains the mapping
     *                    from SQL type names to classes in the Java programming language
     * @return an <code>Object</code> representing the SQL value in the
     * specified column
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException
    {
        //todo this can be implemented
        return null;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>Ref</code> object
     * in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a <code>Ref</code> object representing the SQL <code>REF</code>
     * value in the specified column
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Ref getRef(String columnLabel) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "getRef");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>Blob</code> object
     * in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a <code>Blob</code> object representing the SQL <code>BLOB</code>
     * value in the specified column
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Blob getBlob(String columnLabel) throws SQLException
    {
        throw CBDriver.notImplemented(ResultSet.class, "getBlob");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>Clob</code> object
     * in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a <code>Clob</code> object representing the SQL <code>CLOB</code>
     * value in the specified column
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Clob getClob(String columnLabel) throws SQLException
    {
        throw CBDriver.notImplemented(ResultSet.class, "getClob");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as an <code>Array</code> object
     * in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return an <code>Array</code> object representing the SQL <code>ARRAY</code> value in
     * the specified column
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.2
     */
    @Override
    public Array getArray(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getArrayChecked(columnLabel);
    }
    public Array getArrayChecked(String columnLabel) throws SQLException
    {

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }

        Object json = jsonObject.get(columnLabel);

        if ( wasNull = (json == null) )
        {
            return null;
        }

        if (!(json instanceof List))
        {
            throw new SQLException("Column is not a list ");
        }
        return new CBArray((List)json);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>java.sql.Date</code> object
     * in the Java programming language.
     * This method uses the given calendar to construct an appropriate millisecond
     * value for the date if the underlying database does not store
     * timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param cal         the <code>java.util.Calendar</code> object
     *                    to use in constructing the date
     * @return the column value as a <code>java.sql.Date</code> object;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException
    {
        checkClosed();
        checkIndex();

        Field field = getField(columnIndex);
        String fieldName = field.getName();

        return getDateChecked(fieldName, cal);

    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>java.sql.Date</code> object
     * in the Java programming language.
     * This method uses the given calendar to construct an appropriate millisecond
     * value for the date if the underlying database does not store
     * timezone information.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param cal         the <code>java.util.Calendar</code> object
     *                    to use in constructing the date
     * @return the column value as a <code>java.sql.Date</code> object;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getDateChecked(columnLabel, cal);
    }

    private Date getDateChecked(String columnLabel, Calendar cal) throws SQLException
    {

        Date date;

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }

        Object json = jsonObject.get(columnLabel);

        if ( wasNull = (json == null) )
        {
            return null;
        }

        try
        {
            if (json instanceof java.util.Date)
            {

                date = new Date(((java.util.Date) json).getTime());
            }
            else if (json instanceof Date)
            {
                return (Date)json;
            }
            else if ( json instanceof  String)
            {
                date = new Date(df.parse((String)json).getTime());
            }
            else
            {
                throw new SQLException("value " + json +" is not a Date");

            }
        }
        catch( ParseException ex)
        {
            throw new SQLException("value " + json +" is not a Date",ex);
        }

        if ( cal != null )
        {
            date = timestampUtils.applyCalendar(cal, date);
        }
        return date;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>java.sql.Time</code> object
     * in the Java programming language.
     * This method uses the given calendar to construct an appropriate millisecond
     * value for the time if the underlying database does not store
     * timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param cal         the <code>java.util.Calendar</code> object
     *                    to use in constructing the time
     * @return the column value as a <code>java.sql.Time</code> object;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException
    {
        checkClosed();
        checkIndex();

        Field field = getField(columnIndex);
        String fieldName = field.getName();

        return getTimeChecked(fieldName, cal);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>java.sql.Time</code> object
     * in the Java programming language.
     * This method uses the given calendar to construct an appropriate millisecond
     * value for the time if the underlying database does not store
     * timezone information.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param cal         the <code>java.util.Calendar</code> object
     *                    to use in constructing the time
     * @return the column value as a <code>java.sql.Time</code> object;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid;
     *                               if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getTimeChecked(columnLabel, cal);
    }

    private Time getTimeChecked(String columnLabel, Calendar cal) throws SQLException
    {
        Time time;
        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }

        String json = (String)jsonObject.get(columnLabel);

        if ( json == null ) return null;

        try
        {
            time = new Time(tf.parse(json).getTime());
        }
        catch( Exception ex)
        {
            throw new SQLException("value " + json +" is not a Time", ex);
        }

        // check to see if there is a calendar and that it is different than the one used to parse
        if ( cal != null )
        {
            time = timestampUtils.applyCalendar( cal, time );
        }
        return time;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>java.sql.Timestamp</code> object
     * in the Java programming language.
     * This method uses the given calendar to construct an appropriate millisecond
     * value for the timestamp if the underlying database does not store
     * timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param cal         the <code>java.util.Calendar</code> object
     *                    to use in constructing the timestamp
     * @return the column value as a <code>java.sql.Timestamp</code> object;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnIndex is not valid;
     *                               if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException
    {
        checkClosed();
        checkIndex();

        Field field = getField(columnIndex);
        String fieldName = field.getName();

        return  getTimestampChecked(fieldName, cal);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>java.sql.Timestamp</code> object
     * in the Java programming language.
     * This method uses the given calendar to construct an appropriate millisecond
     * value for the timestamp if the underlying database does not store
     * timezone information.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param cal         the <code>java.util.Calendar</code> object
     *                    to use in constructing the date
     * @return the column value as a <code>java.sql.Timestamp</code> object;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid or
     *                               if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.2
     */
    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getTimestampChecked(columnLabel, cal);
    }
    private Timestamp getTimestampChecked(String columnLabel, Calendar cal) throws SQLException
    {

        Timestamp ts;

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }

        Object json = jsonObject.get(columnLabel);
        if ( wasNull = (json == null) )
        {
            return null;
        }

        try
        {
            if (json instanceof java.util.Date || json instanceof java.sql.Date)
            {
                ts = new Timestamp( ((java.util.Date)json).getTime());
            }
            else if (json instanceof String)
            {
                ts = timestampUtils.parseTimestamp((String)json);
            }
            else
            {
                throw new SQLException("value " + json + " is not a Timestamp");
            }
        }
        catch( Exception ex)
        {
            throw new SQLException("value " + json+ "is not a Timestamp", ex);
        }

        // check to see if there is a calendar and that it is different than the one used to parse
        if ( cal != null )
        {
            ts = timestampUtils.applyCalendar(cal, ts);
        }
        return ts;
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>java.net.URL</code>
     * object in the Java programming language.
     *
     * @param columnIndex the index of the column 1 is the first, 2 is the second,...
     * @return the column value as a <code>java.net.URL</code> object;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code> in the Java programming language
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs; this method
     *                                                  is called on a closed result set or if a URL is malformed
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public URL getURL(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        Field field =  getField(columnIndex);
        String fieldName = field.getName();

        return getURLChecked(fieldName);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>java.net.URL</code>
     * object in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value as a <code>java.net.URL</code> object;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code> in the Java programming language
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs; this method
     *                                                  is called on a closed result set or if a URL is malformed
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public URL getURL(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();
        return getURLChecked(columnLabel);
    }

    private URL getURLChecked(String columnLabel) throws SQLException
    {
        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }
        Object json = jsonObject.get(columnLabel);

        if ( wasNull = (json == null) )
        {
            return null;
        }
        //todo implement
        return null;
    }

    /**
     * Updates the designated column with a <code>java.sql.Ref</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateRef");
    }

    /**
     * Updates the designated column with a <code>java.sql.Ref</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateRef");
    }

    /**
     * Updates the designated column with a <code>java.sql.Blob</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBlob");
    }

    /**
     * Updates the designated column with a <code>java.sql.Blob</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBlob");
    }

    /**
     * Updates the designated column with a <code>java.sql.Clob</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateClob");
    }

    /**
     * Updates the designated column with a <code>java.sql.Clob</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateClob");
    }

    /**
     * Updates the designated column with a <code>java.sql.Array</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateArray");
    }

    /**
     * Updates the designated column with a <code>java.sql.Array</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.4
     */
    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateArray");
    }

    /**
     * Retrieves the value of the designated column in the current row of this
     * <code>ResultSet</code> object as a <code>java.sql.RowId</code> object in the Java
     * programming language.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @return the column value; if the value is a SQL <code>NULL</code> the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public RowId getRowId(int columnIndex) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "getRowId");
    }

    /**
     * Retrieves the value of the designated column in the current row of this
     * <code>ResultSet</code> object as a <code>java.sql.RowId</code> object in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value ; if the value is a SQL <code>NULL</code> the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public RowId getRowId(String columnLabel) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "getRowId");
    }

    /**
     * Updates the designated column with a <code>RowId</code> value. The updater
     * methods are used to update column values in the current row or the insert
     * row. The updater methods do not update the underlying database; instead
     * the <code>updateRow</code> or <code>insertRow</code> methods are called
     * to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param x           the column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateRowId");
    }

    /**
     * Updates the designated column with a <code>RowId</code> value. The updater
     * methods are used to update column values in the current row or the insert
     * row. The updater methods do not update the underlying database; instead
     * the <code>updateRow</code> or <code>insertRow</code> methods are called
     * to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateRowId");
    }

    /**
     * Retrieves the holdability of this <code>ResultSet</code> object
     *
     * @return either <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
     * @throws java.sql.SQLException if a database access error occurs
     *                               or this method is called on a closed result set
     * @since 1.6
     */
    @Override
    public int getHoldability() throws SQLException
    {
        checkClosed();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    /**
     * Retrieves whether this <code>ResultSet</code> object has been closed. A <code>ResultSet</code> is closed if the
     * method close has been called on it, or if it is automatically closed.
     *
     * @return true if this <code>ResultSet</code> object is closed; false if it is still open
     * @throws java.sql.SQLException if a database access error occurs
     * @since 1.6
     */
    @Override
    public boolean isClosed() throws SQLException
    {
        return closed.get();
    }

    /**
     * Updates the designated column with a <code>String</code> value.
     * It is intended for use when updating <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param nString     the value for the column to be updated
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or if a database access error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNString");
    }

    /**
     * Updates the designated column with a <code>String</code> value.
     * It is intended for use when updating <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param nString     the value for the column to be updated
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set;
     *                                                  the result set concurrency is <CODE>CONCUR_READ_ONLY</code>
     *                                                  or if a database access error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNString");
    }

    /**
     * Updates the designated column with a <code>java.sql.NClob</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param nClob       the value for the column to be updated
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set;
     *                                                  if a database access error occurs or
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNClob");
    }

    /**
     * Updates the designated column with a <code>java.sql.NClob</code> value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param nClob       the value for the column to be updated
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set;
     *                                                  if a database access error occurs or
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNClob");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>NClob</code> object
     * in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>NClob</code> object representing the SQL
     * <code>NCLOB</code> value in the specified column
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set
     *                                                  or if a database access error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public NClob getNClob(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        Field field =  getField(columnIndex);
        String fieldName = field.getName();

        return getNClob(fieldName);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>NClob</code> object
     * in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a <code>NClob</code> object representing the SQL <code>NCLOB</code>
     * value in the specified column
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set
     *                                                  or if a database access error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public NClob getNClob(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }
        return null;
    }

    /**
     * Retrieves the value of the designated column in  the current row of
     * this <code>ResultSet</code> as a
     * <code>java.sql.SQLXML</code> object in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>SQLXML</code> object that maps an <code>SQL XML</code> value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();
        Field field =  getField(columnIndex);
        String fieldName = field.getName();

        return getSQLXML(fieldName);
    }

    /**
     * Retrieves the value of the designated column in  the current row of
     * this <code>ResultSet</code> as a
     * <code>java.sql.SQLXML</code> object in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a <code>SQLXML</code> object that maps an <code>SQL XML</code> value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }
        return null;
    }

    /**
     * Updates the designated column with a <code>java.sql.SQLXML</code> value.
     * The updater
     * methods are used to update column values in the current row or the insert
     * row. The updater methods do not update the underlying database; instead
     * the <code>updateRow</code> or <code>insertRow</code> methods are called
     * to update the database.
     * 
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param xmlObject   the value for the column to be updated
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs; this method
     *                                                  is called on a closed result set;
     *                                                  the <code>java.xml.transform.Result</code>,
     *                                                  <code>Writer</code> or <code>OutputStream</code> has not been closed
     *                                                  for the <code>SQLXML</code> object;
     *                                                  if there is an error processing the XML value or
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>.  The <code>getCause</code> method
     *                                                  of the exception may provide a more detailed exception, for example, if the
     *                                                  stream does not contain valid XML.
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateSQLXML");
    }

    /**
     * Updates the designated column with a <code>java.sql.SQLXML</code> value.
     * The updater
     * methods are used to update column values in the current row or the insert
     * row. The updater methods do not update the underlying database; instead
     * the <code>updateRow</code> or <code>insertRow</code> methods are called
     * to update the database.
     * 
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param xmlObject   the column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs; this method
     *                                                  is called on a closed result set;
     *                                                  the <code>java.xml.transform.Result</code>,
     *                                                  <code>Writer</code> or <code>OutputStream</code> has not been closed
     *                                                  for the <code>SQLXML</code> object;
     *                                                  if there is an error processing the XML value or
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>.  The <code>getCause</code> method
     *                                                  of the exception may provide a more detailed exception, for example, if the
     *                                                  stream does not contain valid XML.
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateSQLXML");
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>String</code> in the Java programming language.
     * It is intended for use when
     * accessing  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public String getNString(int columnIndex) throws SQLException
    {
        return getString(columnIndex);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>String</code> in the Java programming language.
     * It is intended for use when
     * accessing  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public String getNString(String columnLabel) throws SQLException
    {
        return getString(columnLabel);
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a
     * <code>java.io.Reader</code> object.
     * It is intended for use when
     * accessing  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>java.io.Reader</code> object that contains the column
     * value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language.
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException
    {
        checkClosed();
        checkIndex();

        //now find the key of the first value
        Field field  = getField(columnIndex);
        return getNCharacterStream(field.getName());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a
     * <code>java.io.Reader</code> object.
     * It is intended for use when
     * accessing  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @return a <code>java.io.Reader</code> object that contains the column
     * value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException
    {
        checkClosed();
        checkIndex();

        Map <String,Object> jsonObject = response.getResults().get(index);
        if (checkColumnLabelMissing(jsonObject, columnLabel))
        {
            return null;
        }
        return null;
    }

    /**
     * Updates the designated column with a character stream value, which will have
     * the specified number of bytes.   The
     * driver does the necessary conversion from Java character format to
     * the national character set in the database.
     * It is intended for use when
     * updating  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNCharacterStream");
    }

    /**
     * Updates the designated column with a character stream value, which will have
     * the specified number of bytes.  The
     * driver does the necessary conversion from Java character format to
     * the national character set in the database.
     * It is intended for use when
     * updating  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      the <code>java.io.Reader</code> object containing
     *                    the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNCharacterStream");
    }

    /**
     * Updates the designated column with an ascii stream value, which will have
     * the specified number of bytes.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateAsciiStream");
    }

    /**
     * Updates the designated column with a binary stream value, which will have
     * the specified number of bytes.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateAsciiStream");
    }

    /**
     * Updates the designated column with a character stream value, which will have
     * the specified number of bytes.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateCharacterStream");
    }

    /**
     * Updates the designated column with an ascii stream value, which will have
     * the specified number of bytes.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateAsciiStream");
    }

    /**
     * Updates the designated column with a binary stream value, which will have
     * the specified number of bytes.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBinaryStream");
    }

    /**
     * Updates the designated column with a character stream value, which will have
     * the specified number of bytes.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      the <code>java.io.Reader</code> object containing
     *                    the new column value
     * @param length      the length of the stream
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateCharacterStream");
    }

    /**
     * Updates the designated column using the given input stream, which
     * will have the specified number of bytes.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream An object that contains the data to set the parameter
     *                    value to.
     * @param length      the number of bytes in the parameter data.
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBlob");
    }

    /**
     * Updates the designated column using the given input stream, which
     * will have the specified number of bytes.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param inputStream An object that contains the data to set the parameter
     *                    value to.
     * @param length      the number of bytes in the parameter data.
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBlob");
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object, which is the given number of characters long.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param reader      An object that contains the data to set the parameter value to.
     * @param length      the number of characters in the parameter data.
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateClob");
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object, which is the given number of characters long.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      An object that contains the data to set the parameter value to.
     * @param length      the number of characters in the parameter data.
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateClob");
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object, which is the given number of characters long.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param reader      An object that contains the data to set the parameter value to.
     * @param length      the number of characters in the parameter data.
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set,
     *                                                  if a database access error occurs or
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNClob");
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object, which is the given number of characters long.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      An object that contains the data to set the parameter value to.
     * @param length      the number of characters in the parameter data.
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set;
     *                                                  if a database access error occurs or
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNClob");
    }

    /**
     * Updates the designated column with a character stream value.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.  The
     * driver does the necessary conversion from Java character format to
     * the national character set in the database.
     * It is intended for use when
     * updating  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateNCharacterStream</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNCharacterStream");
    }

    /**
     * Updates the designated column with a character stream value.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.  The
     * driver does the necessary conversion from Java character format to
     * the national character set in the database.
     * It is intended for use when
     * updating  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateNCharacterStream</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      the <code>java.io.Reader</code> object containing
     *                    the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNCharacterStream");
    }

    /**
     * Updates the designated column with an ascii stream value.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateAsciiStream</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateAsciiStream");

    }

    /**
     * Updates the designated column with a binary stream value.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateBinaryStream</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBinaryStream");
    }

    /**
     * Updates the designated column with a character stream value.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateCharacterStream</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateCharacterStream");
    }

    /**
     * Updates the designated column with an ascii stream value.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateAsciiStream</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateAsciiStream");
    }

    /**
     * Updates the designated column with a binary stream value.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateBinaryStream</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x           the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBinaryStream");

    }

    /**
     * Updates the designated column with a character stream value.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateCharacterStream</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      the <code>java.io.Reader</code> object containing
     *                    the new column value
     * @throws java.sql.SQLException                    if the columnLabel is not valid; if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateCharacterStream");
    }

    /**
     * Updates the designated column using the given input stream. The data will be read from the stream
     * as needed until end-of-stream is reached.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateBlob</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream An object that contains the data to set the parameter
     *                    value to.
     * @throws java.sql.SQLException                    if the columnIndex is not valid; if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBlob");
    }

    /**
     * Updates the designated column using the given input stream. The data will be read from the stream
     * as needed until end-of-stream is reached.
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateBlob</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param inputStream An object that contains the data to set the parameter
     *                    value to.
     * @throws java.sql.SQLException                    if the columnLabel is not valid; if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateBlob");
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateClob</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param reader      An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateClob");
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateClob</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException                    if the columnLabel is not valid; if a database access error occurs;
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     *                                                  or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateClob");
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * 
     * The data will be read from the stream
     * as needed until end-of-stream is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateNClob</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param reader      An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException                    if the columnIndex is not valid;
     *                                                  if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set,
     *                                                  if a database access error occurs or
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateNClob");
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object.
     * The data will be read from the stream
     * as needed until end-of-stream is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     * 
     * 
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     * 
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateNClob</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader      An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException                    if the columnLabel is not valid; if the driver does not support national
     *                                                  character sets;  if the driver can detect that a data conversion
     *                                                  error could occur; this method is called on a closed result set;
     *                                                  if a database access error occurs or
     *                                                  the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.6
     */
    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException
    {
        throw CBDriver.notImplemented(CBResultSet.class, "updateN        throw CBDriver.notImplemented(CBResultSet.class, \"updateClob\");\ngetClob");
    }

    /**
     * <p>Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object and will convert from the
     * SQL type of the column to the requested Java data type, if the
     * conversion is supported. If the conversion is not
     * supported  or null is specified for the type, a
     * <code>SQLException</code> is thrown.
     * 
     * At a minimum, an implementation must support the conversions defined in
     * Appendix B, Table B-3 and conversion of appropriate user defined SQL
     * types to a Java type which implements {@code SQLData}, or {@code Struct}.
     * Additional conversions may be supported and are vendor defined.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param type        Class representing the Java data type to convert the designated
     *                    column to.
     * @return an instance of {@code type} holding the column value
     * @throws java.sql.SQLException                    if conversion is not supported, type is null or
     *                                                  another error occurs. The getCause() method of the
     *                                                  exception may provide a more detailed exception, for example, if
     *                                                  a conversion error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.7
     */
    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException
    {
        checkClosed();
        checkIndex();
        Field field = getField(columnIndex);
        return getObject(field.getName(),type);
    }

    /**
     * <p>Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object and will convert from the
     * SQL type of the column to the requested Java data type, if the
     * conversion is supported. If the conversion is not
     * supported  or null is specified for the type, a
     * <code>SQLException</code> is thrown.
     * 
     * At a minimum, an implementation must support the conversions defined in
     * Appendix B, Table B-3 and conversion of appropriate user defined SQL
     * types to a Java type which implements {@code SQLData}, or {@code Struct}.
     * Additional conversions may be supported and are vendor defined.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.
     *                    If the SQL AS clause was not specified, then the label is the name
     *                    of the column
     * @param type        Class representing the Java data type to convert the designated
     *                    column to.
     * @return an instance of {@code type} holding the column value
     * @throws java.sql.SQLException                    if conversion is not supported, type is null or
     *                                                  another error occurs. The getCause() method of the
     *                                                  exception may provide a more detailed exception, for example, if
     *                                                  a conversion error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
     *                                                  this method
     * @since 1.7
     */
    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException
    {
        checkClosed();
        checkIndex();

        // TODO this is not complete
        if ( type.getName().equals("java.sql.Timestamp"))
        {
            //noinspection unchecked
            return (T) getTimestampChecked(columnLabel,null);
        }
        else if ( type.getName().equals("java.sql.Date"))
        {
            //noinspection unchecked
            return (T) getDateChecked(columnLabel,null);
        }

        throw new SQLException("Conversion not supported to {}", type.getName());
    }

    /**
     * Returns an object that implements the given interface to allow access to
     * non-standard methods, or standard methods not exposed by the proxy.
     * 
     * If the receiver implements the interface then the result is the receiver
     * or a proxy for the receiver. If the receiver is a wrapper
     * and the wrapped object implements the interface then the result is the
     * wrapped object or a proxy for the wrapped object. Otherwise return the
     * the result of calling <code>unwrap</code> recursively on the wrapped object
     * or a proxy for that result. If the receiver is not a
     * wrapper and does not implement the interface, then an <code>SQLException</code> is thrown.
     *
     * @param iface A Class defining an interface that the result must implement.
     * @return an object that implements the interface. May be a proxy for the actual implementing object.
     * @throws java.sql.SQLException If no object found that implements the interface
     * @since 1.6
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        checkClosed();
        if (iface.isAssignableFrom(getClass()))
        {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    /**
     * Returns true if this either implements the interface argument or is directly or indirectly a wrapper
     * for an object that does. Returns false otherwise. If this implements the interface then return true,
     * else if this is a wrapper then return the result of recursively calling <code>isWrapperFor</code> on the wrapped
     * object. If this does not implement the interface and is not a wrapper, return false.
     * This method should be implemented as a low-cost operation compared to <code>unwrap</code> so that
     * callers can use this method to avoid expensive <code>unwrap</code> calls that may fail. If this method
     * returns true then calling <code>unwrap</code> with the same argument should succeed.
     *
     * @param iface a Class defining an interface.
     * @return true if this implements the interface or directly or indirectly wraps an object that does.
     * @throws java.sql.SQLException if an error occurs while determining whether this is a wrapper
     *                               for an object with the given interface.
     * @since 1.6
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        checkClosed();
        return iface.isAssignableFrom(getClass());
    }
    /*

     */
    Field getField(int columnNumber) throws SQLException
    {
        columnNumber--;
        if ( columnNumber <0 || columnNumber > fields.size() )
        {
            throw new SQLException("Invalid column Number");
        }
        return fields.get(columnNumber);
    }

    void checkIndex() throws SQLException
    {
        if ( index < 0) throw new SQLException("Invalid index, call ResultSet.next()??");
        if ( index > response.getMetrics().getResultCount() ) throw new SQLException("Invalid index");
    }
    void checkClosed() throws SQLException
    {
        if (closed.get()) throw new SQLException("ResultSet is closed");
    }

    boolean checkColumnLabelMissing(Map<String, Object> jsonObject, String label)  throws SQLException
    {
        boolean fieldExists     = (findField(label) != null);
        boolean existsInResults = jsonObject.containsKey(label);

        //This will return true if the last field read was in the signature, but was not in the results
        isMissing = fieldExists && !existsInResults;


        // not available anywhere throw an exception
        if ( !fieldExists && !existsInResults  )
            throw new SQLException("ResultSet does not contain " + label);


        // we set this here before doing anything else as we check this before getting any values
        wasNull=false;

        return isMissing;
    }
    private Field findField(String fieldName)
    {

        for (Field field:fields)
        {
            if (field.getName().equals(fieldName))
            {
                return field;
            }
        }
        return null;
    }
    public boolean wasMissing()
    {
        return isMissing;
    }
}

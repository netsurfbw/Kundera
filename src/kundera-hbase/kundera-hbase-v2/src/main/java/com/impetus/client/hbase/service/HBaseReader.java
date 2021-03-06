/*******************************************************************************
 * * Copyright 2015 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.client.hbase.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.util.Bytes;

import com.impetus.client.hbase.HBaseDataWrapper;
import com.impetus.client.hbase.Reader;
import com.impetus.client.hbase.utils.HBaseUtils;

/**
 * The Class HBaseReader.
 * 
 * @author Pragalbh Garg
 */
public class HBaseReader implements Reader
{

    /** The scanner. */
    private ResultScanner scanner = null;

    /** The results iter. */
    private Iterator<Result> resultsIter;

    /** The fetch size. */
    private Integer fetchSize;

    /** The counter. */
    private Integer counter = 0;

    /** The table name. */
    private String tableName = null;

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.Reader#LoadData(org.apache.hadoop.hbase.client
     * .Table, java.lang.String, java.lang.Object,
     * org.apache.hadoop.hbase.filter.Filter, java.lang.String[])
     */
    @Override
    public List<HBaseDataWrapper> LoadData(Table hTable, String columnFamily, Object rowKey, Filter filter,
            String... columns) throws IOException
    {
        setTableName(hTable);
        List<HBaseDataWrapper> results = new ArrayList<HBaseDataWrapper>();
        if (scanner == null)
        {

            Scan scan = null;
            if (rowKey != null)
            {
                byte[] rowKeyBytes = HBaseUtils.getBytes(rowKey);
                Get get = new Get(rowKeyBytes);
                if (columnFamily != null)
                {
                    get.addFamily(Bytes.toBytes(columnFamily));
                }

                if (filter != null)
                {
                    get.setFilter(filter);
                }
                Result result = hTable.get(get);

                if (!result.isEmpty() && result != null && result.listCells() != null)
                {
                    HBaseDataWrapper data = new HBaseDataWrapper(hTable.getName().getNameAsString(), result.getRow());
                    data.setColumns(result.listCells());
                    results.add(data);
                }
                return results;
            }
            else
            {
                scan = new Scan();
            }
            setScanCriteria(filter, columnFamily, null, scan, columns);
            scanner = hTable.getScanner(scan);
            resultsIter = scanner.iterator();
        }
        return scanResults(hTable.getName().getNameAsString(), results);
    }

    /**
     * Sets the table name.
     * 
     * @param hTable
     *            the new table name
     */
    private void setTableName(Table hTable)
    {
        this.tableName = hTable.getName().getNameAsString();

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.Reader#LoadData(org.apache.hadoop.hbase.client
     * .HTable, java.lang.String)
     */
    @Override
    public List<HBaseDataWrapper> LoadData(Table hTable, Object rowKey, Filter filter, String... columns)
            throws IOException
    {
        return LoadData(hTable, hTable.getName().toString(), rowKey, filter, columns);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.Reader#loadAll(org.apache.hadoop.hbase.client
     * .HTable, org.apache.hadoop.hbase.filter.Filter, byte[], byte[])
     */
    @Override
    public List<HBaseDataWrapper> loadAll(Table hTable, Filter filter, byte[] startRow, byte[] endRow,
            String columnFamily, String qualifier, String[] columns) throws IOException
    {
        setTableName(hTable);
        List<HBaseDataWrapper> results = new ArrayList<HBaseDataWrapper>();
        if (scanner == null)
        {
            Scan scan = null;
            if (startRow != null && endRow != null && startRow.equals(endRow))
            {
                Get get = new Get(startRow);
                scan = new Scan(get);
            }
            else if (startRow != null && endRow != null)
            {
                scan = new Scan(startRow, endRow);
            }
            else if (startRow != null)
            {
                scan = new Scan(startRow);
            }
            else if (endRow != null)
            {
                scan = new Scan();
                scan.setStopRow(endRow);
            }
            else
            {
                scan = new Scan();
            }
            setScanCriteria(filter, columnFamily, qualifier, scan, columns);
            scanner = hTable.getScanner(scan);
            resultsIter = scanner.iterator();
        }
        return scanResults(tableName, results);
    }

    /**
     * Sets the scan criteria.
     * 
     * @param filter
     *            the filter
     * @param columnFamily
     *            the column family
     * @param qualifier
     *            the qualifier
     * @param s
     *            the s
     * @param columns
     *            the columns
     */
    private void setScanCriteria(Filter filter, String columnFamily, String qualifier, Scan s, String[] columns)
    {
        if (filter != null)
        {
            s.setFilter(filter);
        }
        if (columnFamily != null && qualifier != null)
        {
            s.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes(qualifier));
        }
        else if (columnFamily != null)
        {
            s.addFamily(Bytes.toBytes(columnFamily));
        }

        if (columns != null && columns.length > 0)
        {
            for (String columnName : columns)
            {
                if (columnFamily != null && columnName != null)
                {
                    s.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes(columnName));
                }
            }
        }
    }

    /**
     * Scan results.
     * 
     * @param tableName
     *            the table name
     * @param results
     *            the results
     * @return the list
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private List<HBaseDataWrapper> scanResults(final String tableName, List<HBaseDataWrapper> results)
            throws IOException
    {
        if (fetchSize == null)
        {
            for (Result result : scanner)
            {
                List<Cell> cells = result.listCells();
                HBaseDataWrapper data = new HBaseDataWrapper(tableName, result.getRow());
                data.setColumns(cells);
                results.add(data);
            }

            scanner = null;
            resultsIter = null;
        }
        return results;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.Reader#scanRowKeys(org.apache.hadoop.hbase.client
     * .Table, org.apache.hadoop.hbase.filter.Filter, java.lang.String,
     * java.lang.String, java.lang.Class)
     */
    @Override
    public Object[] scanRowKeys(final Table hTable, final Filter filter, final String columnFamilyName,
            final String columnName, final Class rowKeyClazz) throws IOException
    {
        List<Object> rowKeys = new ArrayList<Object>();

        if (scanner == null)
        {
            Scan s = new Scan();
            s.setFilter(filter);
            s.addColumn(Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            scanner = hTable.getScanner(s);
            resultsIter = scanner.iterator();
        }

        if (fetchSize == null)
        {
            for (Result result : scanner)
            {
                for (Cell cell : result.listCells())
                {
                    rowKeys.add(HBaseUtils.fromBytes(CellUtil.cloneFamily(cell), rowKeyClazz));
                }
            }
        }
        if (rowKeys != null && !rowKeys.isEmpty())
        {
            return rowKeys.toArray(new Object[0]);
        }
        return null;
    }

    /**
     * Load all.
     * 
     * @param hTable
     *            the h table
     * @param rows
     *            the rows
     * @param columnFamily
     *            the column family
     * @param columns
     *            the columns
     * @return the list
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public List<HBaseDataWrapper> loadAll(final Table hTable, final List<Object> rows, final String columnFamily,
            final String[] columns) throws IOException
    {
        setTableName(hTable);
        List<HBaseDataWrapper> results = new ArrayList<HBaseDataWrapper>();
        List<Get> getRequest = new ArrayList<Get>();
        for (Object rowKey : rows)
        {
            if (rowKey != null)
            {
                byte[] rowKeyBytes = HBaseUtils.getBytes(rowKey);
                Get request = new Get(rowKeyBytes);
                getRequest.add(request);
            }
        }
        Result[] rawResult = hTable.get(getRequest);
        for (Result result : rawResult)
        {
            List<Cell> cells = result.listCells();
            if (cells != null)
            {
                HBaseDataWrapper data = new HBaseDataWrapper(tableName, result.getRow());
                data.setColumns(cells);
                results.add(data);
            }
        }
        return results;
    }

    /**
     * Sets the fetch size.
     * 
     * @param fetchSize
     *            the new fetch size
     */
    public void setFetchSize(final int fetchSize)
    {
        this.fetchSize = fetchSize;
    }

    /**
     * Next.
     * 
     * @return the h base data
     */
    public HBaseDataWrapper next()
    {
        Result result = resultsIter.next();
        List<Cell> cells = result.listCells();
        HBaseDataWrapper data = new HBaseDataWrapper(tableName, result.getRow());
        data.setColumns(cells);
        return data;
    }

    /**
     * Checks for next.
     * 
     * @return true, if successful
     */
    public boolean hasNext()
    {
        if (scanner == null)
        {
            return false;
        }
        else
        {
            if (fetchSize != null)
            {
                if (counter < fetchSize)
                {
                    counter++;
                    return resultsIter.hasNext();
                }
            }
            else
            {
                return resultsIter.hasNext();
            }
        }
        return false;
    }

    /**
     * Reset.
     */
    public void reset()
    {
        scanner = null;
        fetchSize = null;
        resultsIter = null;
        tableName = null;
        counter = 0;
    }
}

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

package com.couchbase;

/**
 * Created by davec on 2015-07-08.
 */
import java.sql.*;

public class Query {

    static String ConnectionURL = "jdbc:couchbase://54.237.32.30:8093";

    public static void main(String[] args) {
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;

        String query = "SELECT * FROM customer LIMIT 1";
        try {
            con = DriverManager.getConnection(ConnectionURL);
            stmt = con.createStatement();
            rs = stmt.executeQuery(query);

            DatabaseMetaData metadata = con.getMetaData();
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnsNumber = rsmd.getColumnCount();
            rsmd = rs.getMetaData();
            columnsNumber = rsmd.getColumnCount();
            while (rs.next()) {
                for (int i = 0; i < columnsNumber; i++) {
                    System.out.println(rsmd.getColumnName(i+1) + ":"
                            + rs.getString(i+1) + " ");
                }
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException se1) {
            }
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException se2) {
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException se4) {
                se4.printStackTrace();
            }
        }
    }
}

package org.jek.misql;


import java.sql.Connection;

/** jConnection<br>
 * Class to hold a JDBC connection and other public fields about it.
 * @author jek
 *
 */
public class jConnection {

	public String connName;
	public Connection conn;
	public String dbName = "";
	public String userName = "";
	public String server = "";

	public jConnection(String name) {
		this.connName = name;
	}

	public jConnection(Connection conn2) {
		// TODO Auto-generated constructor stub
	}

	public jConnection(Connection conn2, String string) {
		this.conn = conn2;
		this.connName = string;
	}

	public jConnection(Connection conn2, String connName, String dbName, String userName, String server) {
		this.conn = conn2;
		this.connName = connName;
		this.dbName = dbName;
		this.userName = userName;
		this.server = server;
	}
}

/**
 * 
 */
package org.jek.misql;

import java.sql.SQLException;

import com.sybase.jdbcx.SybMessageHandler;

/** implements SybMessageHandler<br>
 * Installed into SybDriver and calls MisqlmessHandler.
 * @return null if handled by MisqlmessHandler, else the SQLException
 * @author jek
 */
class MiMessageHandler implements SybMessageHandler
{
	public  SQLException messageHandler(SQLException sqe)
	{
		// System.err.println("In messageHandler with "+sqe);
		return Misql.messHandler(sqe);
	}
}



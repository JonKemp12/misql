/**
 * 
 */
package org.jek.misql;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;


/**
 * @author jek
 *
 */
public class Hresults {
	private static final int STRING = 0;
	private static final int NUMBER = 1;
	private static final int MONEY = 2;
	private static final int DATETIME = 3;
	private int numColumns;
	ArrayList<ColFormat> formatList = new ArrayList<ColFormat>();
	private ResultSet rs;
	

	public Hresults(ResultSet rs, int dispWidth) throws SQLException {
		this.rs = rs;
		ResultSetMetaData rsmd;
		rsmd = rs.getMetaData();
		numColumns = rsmd.getColumnCount();
		int dispCol = 0;		// Count how far across display we are

		for (int i = 1; i <= numColumns; i++)
		{
			String colName = rsmd.getColumnName(i);
			int	dispSize;
			String fmtPrefix = " ";
			int colType;
			ColFormat colFormat;
		
			switch (rsmd.getColumnType(i)) {
			case java.sql.Types.DECIMAL:
			case java.sql.Types.DOUBLE:
			case java.sql.Types.FLOAT:
			case java.sql.Types.NUMERIC:
			case java.sql.Types.REAL:
			case java.sql.Types.BIGINT:
			case java.sql.Types.INTEGER:
			case java.sql.Types.SMALLINT:
			case java.sql.Types.TINYINT:
				// All number types are right justified:
				colType = STRING;
				if (rsmd.isCurrency(i)) {
					// Format for money type with 2 decimal places
					colType = MONEY;
				}
				break;

			case java.sql.Types.TIMESTAMP:
				colType = DATETIME;
				break;
				
			default:
				// Everything else is left justified
				colType = STRING;
			break;
			}
			
			// Here to set the display size!
			dispSize = rsmd.getColumnDisplaySize(i);
			if (colName.length() > dispSize) {
				dispSize = colName.length();
			}
			
			dispCol = dispCol + dispSize + 1;
			if (dispCol > dispWidth) {					// Need newline
				fmtPrefix = "\n\t ";			// ISQL starts with "\n\t "
				dispCol = 9 + dispSize;
			}

			colFormat = new ColFormat(i,colName,dispSize,fmtPrefix,colType);
			formatList.add(colFormat);			
		}
	}

	public void printHeader(PrintStream _out) {
		for (ColFormat col : formatList) {
			int dispSize = col.dispSize;
				
			// print column heading (left justified)
			_out.printf(col.fmtPrefix+"%-"+dispSize+"s", col.colName);
		}
		_out.println(" ");		// End the row with " \n";		
		
		// Now do underlining:
		for (ColFormat col : formatList) {
			int dispSize = col.dispSize;
						
			// print column underline (left justified)
			_out.printf(col.fmtPrefix+"%-"+dispSize+"s", Utils.replicate("-",dispSize));
		}
		_out.println(" ");		// End the row with " \n";		
	}

	public void printRows(PrintStream _out) throws SQLException {
		MiFormat mif = new MiFormat(_out);		// Build a formatter for various types
		
		for(int rowNum = 1; rs.next(); rowNum++)
		{
			for (ColFormat col : formatList) {
				switch (col.colType) {
				case NUMBER: // Numbers are right justified strings
					_out.printf(col.fmtPrefix+"%"+col.dispSize+"s", rs.getString(col.colNum));
					break;
					
				case MONEY:	// Money is formatted, right justified
					_out.printf(col.fmtPrefix+"%"+col.dispSize+"s", 
							mif.toMoney(rs.getBigDecimal(col.colNum)));
					break;

				case DATETIME:	// Datetime is formatted and right justified
					_out.printf(col.fmtPrefix+"%"+col.dispSize+"s", 
							mif.toDateTime(rs.getDate(col.colNum), rs.getTime(col.colNum)));
					break;
					
				case STRING:	// Everythingelse is left justified string					
				default:
					_out.printf(col.fmtPrefix+"%-"+col.dispSize+"s", rs.getString(col.colNum));
					break;
				}				
			}
			_out.println(" ");		// End the row with " \n";		
		}
	}

	public void printFooter(PrintStream _out) throws SQLException {
        int rowsAffected = rs.getStatement().getUpdateCount();
        if (rowsAffected >= 0)
        {
            _out.println("("+rowsAffected + ") rows affected");
        }
		
	}
	
	private static class ColFormat		// Private class to hold format and colname string pairs.
	{
		public int colNum;
		public String colName = null;
		public int dispSize;
		public String fmtPrefix;
		public int colType;

		// Constructor
		public ColFormat(int colNum, String colName, int dispSize, String fmtPrefix, int colType)
		{
			this.colNum = colNum;
			this.colName = colName;
			this.dispSize = dispSize;
			this.fmtPrefix = fmtPrefix;
			this.colType = colType;
		}
	}
}

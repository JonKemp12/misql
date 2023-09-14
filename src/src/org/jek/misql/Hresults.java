package org.jek.misql;



import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;

import com.sybase.jdbc3.jdbc.SybResultSet;



/**
 * Hresults : object to format horizontal data rows.
 * @author jek
 * History:
 *  jek		21 Aug 2008:	Created.
 *  jek		 8 Sep 2011:	Added maxColWidth option.
 *  jek		13 Sep 2011:	Rework to support colTerm & rowTerm options
 */
public class Hresults {
	private int numColumns;
	ArrayList<ColFormat> formatList = new ArrayList<ColFormat>();
	private ResultSet rs;
	private String colTerm;
	private String rowTerm;
	private int dispWidth;
	private MiFormat mif;
	
// Hresults(rs, _dispWidth, _colTerm, _rowTerm)
	/** Hresults:<br>
This object sets up to display horizontal results.
If colTerm is not set, then the default is as ISQL; each
row begins with a single space, columns are padded with spaces and justified.
If display is exceed, a new line is started with a tab character.<br>
If colTerm is set then that string is used between each column output. If the first
character is a space, then the output is padded and justified to left & right.
Last column is terminated with rowTerm - default is newline.<br>
	 * @param rs			Resultset
	 * @param dispWidth		Width of display (80)
	 * @param maxColWidth	Max width for any column (512)
	 * @param colTerm		String to terminate each column except last ("")
	 * @param rowTerm		String to terminate last column.
	 * @throws SQLException
	 */
	public Hresults(ResultSet rs, int dispWidth, int maxColWidth,
			String colTerm, String rowTerm) throws SQLException {
		this.rs = rs;
		this.dispWidth = dispWidth;
		this.colTerm = colTerm;
		this.rowTerm = rowTerm;
		
		mif = new MiFormat();		// Build a formatter for various types		
		ResultSetMetaData rsmd;
		rsmd = rs.getMetaData();
		numColumns = rsmd.getColumnCount();
		
		
		for (int i = 1; i <= numColumns; i++)
		{
			String colName = rsmd.getColumnLabel(i);
			int	dispSize;
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
				colType = MiFormat.NUMBER;
				if (rsmd.isCurrency(i)) {
					// Format for money type with 2 decimal places
					colType = MiFormat.MONEY;
				}
				break;

			case java.sql.Types.TIMESTAMP:
				colType = MiFormat.DATETIME;
				break;
				
			case java.sql.Types.DATE:
				colType = MiFormat.DATE;
				break;
				
			case java.sql.Types.TIME:
				colType = MiFormat.TIME;
				break;
				
			default:
				// Everything else is left justified
				colType = MiFormat.STRING;
			break;
			}
			
			// Here to set the display size!
			dispSize = rsmd.getColumnDisplaySize(i);
			if (colName.length() > dispSize) {
				dispSize = colName.length();
			}
			
			/* Limit dispSize to maxColWidth (ISQL default is 512) */
			if (dispSize > maxColWidth)
				dispSize = maxColWidth;
			
			colFormat = new ColFormat(i,i,colName,dispSize,colType);
			formatList.add(colFormat);			
		}
	}

	public void printHeader(MiPrinter out) {
		int dispCol = 0;		// Count how far across display we are
		int dispSize = 0;
		String dispStr = "";
		
		for (ColFormat col : formatList) {
			dispSize = col.dispSize;

			if (colTerm.length() == 0) {	// no colTerm set (ISQL default)
				if (col.colNum == 1){		// If first column begin a space.
					out.print(" ");
					dispCol = 1;
				}
				// Create padded heading
				dispStr = mif.leftString(col.colName, dispSize, " ");
				
				dispCol += dispStr.length();
				if (dispCol > dispWidth) {	// Need newline
					out.print("\n\t ");
					dispCol = 9 + dispStr.length();
				}
			} else {	// colTerm is set so
				if (col.colNum == formatList.size())	// Last col so no colTerm
					dispStr = mif.leftString(col.colName, dispSize, "");			
				else
					dispStr = mif.leftString(col.colName, dispSize, colTerm);
			}
	
			// Output the header
			out.print(dispStr);
		}
		out.print(rowTerm);		// End the row with rowTerm string;		
		
		// Now do underlining:
		for (ColFormat col : formatList) {
			dispSize = col.dispSize;
						
			if (colTerm.length() == 0) {	// no colTerm set (ISQL default)
				if (col.colNum == 1){		// If first column begin a space.
					out.print(" ");
					dispCol = 1;
				}
				// Create padded underline
				dispStr = mif.leftString(Utils.replicate("-",dispSize), dispSize, " ");
				
				dispCol += dispStr.length();
				if (dispCol > dispWidth) {	// Need newline
					out.print("\n\t ");
					dispCol = 9 + dispStr.length();
				}
			} else {	// colTerm is set so
				if (col.colNum == formatList.size())	// Last col so no colTerm
					dispStr = mif.leftString(Utils.replicate("-",dispSize), dispSize, "");			
				else
					dispStr = mif.leftString(Utils.replicate("-",dispSize), dispSize, colTerm);
			}

			// print column underline 
			out.print(dispStr);
		}
		out.print(rowTerm);		// End the row with rowTerm string;		
	}

	public void printRows(MiPrinter out) throws SQLException {
		
		for(int rowNum = 1; rs.next(); rowNum++)
		{
			int dispCol = 0;		// Count how far across display we are
			String dispStr = "";
			
			for (ColFormat col : formatList) {
				
				if (colTerm.length() == 0) {	// no colTerm set (ISQL default)
					if (col.colNum == 1){		// If first column begin a space.
						out.print(" ");
						dispCol = 1;
					}
					// Create padded column string
					dispStr = mif.toString(rs, col, " ");
					
					dispCol += dispStr.length();
					if (dispCol > dispWidth) {	// Need newline
						out.print("\n\t ");
						dispCol = 9 + dispStr.length();
					}
				} else {	// colTerm is set so
					if (col.colNum == formatList.size())	// Last col so no colTerm
						dispStr = mif.toString(rs, col, "");			
					else
						dispStr = mif.toString(rs, col, colTerm);
				}
				// Output the column string
				out.print(dispStr);
			}
			out.print(rowTerm);		// End the row with rowTerm string;		
		}
		out.println("");
	}

	public void printFooter(PrintStream _out) throws SQLException {
        int rowsAffected = rs.getStatement().getUpdateCount();
        if (rowsAffected >= 0)
        {
            _out.println("("+rowsAffected + ") rows affected");
        }
		
	}
}

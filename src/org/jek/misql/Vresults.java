/**
 * 
 */
package org.jek.misql;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;

import com.sybase.jdbc3.jdbc.SybResultSet;

/** Class to format data into vertical rows<p>
 * The width of the columns of data are calculated by subtracting the max width of the column headings
 from the display width and dividing by the number of vertical columns required.
 * @author jek
 * History:
 * 14 Sep 2011 - jek: Extensive rework to handle colTerm & rowTerm settings
 * 					  requiring new ColFormat and MiFormat objects.
 */
public class Vresults {
	private int numColumns;
	ArrayList<ColFormat> formatList = new ArrayList<ColFormat>();
	private ResultSet rs;
	private int headSize = 1;
	private int dataSize;
	private int vRows;
	private MiFormat mif;
	private String colTerm = "";	// Column terminator string (default ' | ')
	private String rowTerm;


	public Vresults(ResultSet rs, int dispWidth, int vRows, int maxColWidth,
			String colTerm, String rowTerm) throws SQLException {
		this.rs = rs;
		this.vRows = vRows;
		this.rowTerm = rowTerm;
		mif = new MiFormat();		// Build a formatter for various types		
		ResultSetMetaData rsmd;
		rsmd = rs.getMetaData();
		numColumns = rsmd.getColumnCount();
		
		if (colTerm.length() == 0)
			colTerm  = " | ";
		this.colTerm = colTerm;
		int colTermLen = colTerm.length();

		for (int i = 1; i <= numColumns; i++)
		{
			String colName = rsmd.getColumnLabel(i).trim();
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

			default:
				// Everything else is left justified
				colType = MiFormat.STRING;
			break;
			}

			// Here to set the display size!
			if(colName.length() > headSize)
				headSize = colName.length();

			if (rsmd.getColumnDisplaySize(i) > dataSize) {
				dataSize = rsmd.getColumnDisplaySize(i);
			}

			// colFormat = new ColFormat(i,colName,colType);
			colFormat = new ColFormat(i,i,colName,1,colType);
			formatList.add(colFormat);			
		}
		
		if (headSize > maxColWidth)		// Column headings restricted to maxColWidth
			headSize = maxColWidth;
		if (dataSize > maxColWidth)		// Column data restricted to maxColWidth
			dataSize = maxColWidth;
		
		// Column names and data values have colTerm added in printout
		if ((headSize+colTermLen + rowTerm.length() +
				((dataSize+colTermLen) * vRows)) > dispWidth) {	// Need to resize data
			dataSize = ((dispWidth - (headSize+colTermLen + rowTerm.length())) / vRows) - colTermLen;			
		}
		
		// Now set up the print formats in the formatList
		for (ColFormat col : formatList) {
			col.dispSize = dataSize;
		}
	}

	public void printRows(MiPrinter _out) throws SQLException {
		String[][] dataCols;
		dataCols = new String[vRows][numColumns];

		int fetchedRow = 0;

		do {
			for (fetchedRow = 0; 
			fetchedRow < vRows && rs.next(); 
			fetchedRow++) {							// For each vertical row
				for (ColFormat col : formatList) {
					dataCols[fetchedRow][col.colNum-1] = mif.toString(rs, col, colTerm);
				}
			}

			if (fetchedRow == 0) {	// No more rows to print
				break;
			}

			// Now print the column heading and data
			for (ColFormat col : formatList) {
				// print column heading (left justified)
				// _out.printf(" %"+headSize+"s |", col.colName);
				_out.print(mif.rightString(col.colName, headSize, colTerm));

				for (int vr = 0; vr < fetchedRow; vr++) {
					// _out.printf(col.fmtStr, dataCols[vr][col.colNum-1]);
					_out.print(dataCols[vr][col.colNum-1]);
				}
				_out.print(rowTerm);		// newline
			}
			_out.print(rowTerm);		// empty line between pages

		} while (fetchedRow == vRows);	
	}
}


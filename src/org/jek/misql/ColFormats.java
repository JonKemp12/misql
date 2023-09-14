package org.jek.misql;

import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;

public class ColFormats {

	private int numColumns;
	ArrayList<ColFormat> formatList = new ArrayList<ColFormat>();
	private ResultSet rs;
	
	public ColFormats(ResultSet rs) throws SQLException {

		this.rs = rs;
		ResultSetMetaData rsmd;
		rsmd = rs.getMetaData();
		numColumns = rsmd.getColumnCount();

		for (int i = 1; i <= numColumns; i++)
		{
			String colName = rsmd.getColumnName(i);
			ColFormat colFormat;
			int	dispSize;

			// Here to set the display size!
			dispSize = rsmd.getColumnDisplaySize(i);
			if (colName.length()> dispSize) {
				dispSize = colName.length();
			}
			
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
				if (rsmd.isCurrency(i)) {
					// Format for money type
				}
				break;

			default:
				// Everything else is left justified
				dispSize = -dispSize;
			break;
			}

			colFormat = new ColFormat(colName,dispSize);
			formatList.add(colFormat);			
		}
	}
	
	
	public void printHeader(PrintStream _out, int dispWidth) {
		int dispCol = 0;		// Count how far across display we are
		for (ColFormat col : formatList) {
			String fmt = " ";
			int dispSize = col.dispSize;
			if ( dispSize < 0 )
				dispSize = dispSize * -1;
				
			dispCol = dispCol + dispSize + 1;

			if (dispCol > dispWidth) {				// Need newline
				fmt = "\n\t ";						// ISQL starts with "\n\t "
				dispCol = 9 + dispSize;
			}
			
			// print column heading (left justified)
			_out.printf(fmt+"%-"+dispSize+"s", col.colName);
		}
		_out.println(" ");		// End the row with " \n";		
		
		// Now do underlining:
		for (ColFormat col : formatList) {
			String fmt = " ";
			int dispSize = col.dispSize;
			if ( dispSize < 0 )
				dispSize = dispSize * -1;			// Absolute value
				
			dispCol = dispCol + dispSize + 1;

			if (dispCol > dispWidth) {				// Need newline
				fmt = "\n\t ";						// ISQL starts with "\n\t "
				dispCol = 9 + dispSize;
			}
			
			// print column heading (left justified
			_out.printf(fmt+"%-"+dispSize+"s", Utils.replicate("-",dispSize));
		}
		_out.println(" ");		// End the row with " \n";		
	};
	
	public void printRows(PrintStream _out, int dispWidth) throws SQLException {
		for(int rowNum = 1; rs.next(); rowNum++)
		{
			int i = 1;
			int dispCol = 0;
			for (ColFormat col : formatList) {
				String fmt = " ";
				int dispSize = col.dispSize;
				if ( dispSize < 0 )
					dispSize = dispSize * -1;			// Absolute value
					
				dispCol = dispCol + dispSize + 1;

				if (dispCol > dispWidth) {				// Need newline
					fmt = "\n\t ";						// ISQL starts with "\n\t "
					dispCol = 9 + dispSize;
				}
				
				// print column heading (left justified
				_out.printf(fmt+"%"+col.dispSize+"s", rs.getString(i++));
			}
			_out.println(" ");		// End the row with " \n";		
		}
	}
	
	static class ColFormat		// Private class to hold format and colname string pairs.
	{
		public String colName = null;
		public int dispSize;

		// Constructor
		public ColFormat(String colName, int dispSize)
		{
			this.colName = colName;
			this.dispSize = dispSize;
		}
	}

	public void printFooter(PrintStream _out) {
		// TODO Auto-generated method stub
		
	}



}

package org.jek.misql;


import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.Formatter;

/** MiFormat is used to format cell data into consistent strings
 * <br> For each SQL type
 * @author jek
 *
 */

public class MiFormat {
	static final int STRING = 0;
	static final int NUMBER = 1;
	static final int MONEY = 2;
	static final int DATETIME = 3;
	static final int DATE = 4;
	static final int TIME = 5;

	private StringBuilder sb;
	private Formatter formatter;

	public MiFormat() {
		sb = new StringBuilder(20);
		formatter = new Formatter(sb);
	}

	public String toMoney(BigDecimal value) {
		sb.delete(0, sb.length());
		if (value == null)
			return "NULL";
		
		if (value.compareTo(BigDecimal.ZERO) == 0)
			return "0.00";
			
		formatter.format("%,.2f", value);												
		return sb.toString();
	}

	public String toDateTime(Date date) {
		sb.delete(0, sb.length());
		
		if (date == null)
			return "NULL";
		
		formatter.format("%1$tb %1$te %1$tY", date);
		return sb.toString();
	}

	public String toDateTime(Time time) {
		sb.delete(0, sb.length());
		
		if (time == null)
			return "NULL";
		
		formatter.format("%1$tI:%1$tM%1$Tp", time);
		return sb.toString();
	}

	public String toDateTime(Date date, Time time) {
		sb.delete(0, sb.length());
		
		if (date == null)
			return "NULL";
		
		formatter.format("%1$tb %1$te %1$tY %2$tI:%2$tM%2$Tp", date, time);
		return sb.toString();
	}

	/** leftString : generates left justified string. <br>
	If length is greater that dispSize, then it is truncated with a trailing '>' <br>
	Else, if termStr begins with space character then output is right padded to maxSize 
	and then output with termStr appended <br>
	otherwise output is simply inStr+termStr. <br>
	 * @param inStr		- input string
	 * @param maxSize	- maximum output string length
	 * @param termStr	- 
	 * @return
	 */
	public String leftString(String inStr, int maxSize, String termStr) {
		if (inStr == null)
			inStr = "NULL";
		
		if (inStr.length() > 0 && inStr.charAt(0) == '\0')	// Seen this form sysprocesses.program_name
			inStr = "";
		
		// If longer than max
		if (inStr.length() > maxSize) {
			// return truncated plus '>' plus termStr
			return(inStr.substring(0, maxSize-1)+ ">" + termStr);
		}
		
		if (termStr.startsWith(" ")) {// pad on right with spaces
			return(inStr + Utils.replicate(" ", maxSize - inStr.length()) + termStr);			
		}
		return inStr + termStr;
	}
	
	/** rightString : generates right justified string. <br>
	If length is greater that dispSize, then it is truncated with a leading '<' <br>
	Else, if termStr begins with space character then output is left padded to maxSize 
	and then output with termStr appended <br>
	otherwise output is simply inStr+termStr. <br>
	 * @param inStr		- input string
	 * @param maxSize	- maximum output string length
	 * @param termStr	- 
	 * @return
	 */
	public String rightString(String inStr, int maxSize, String termStr) {
		if (inStr == null)
			inStr = "NULL";
		if (inStr.length() > 0 && inStr.charAt(0) == '\0')	// Seen this form sysprocesses.program_name
			inStr = "";
		
		int len;
		// If longer than max
		if ((len = inStr.length()) > maxSize) {
			// return '<' plus truncated plus termStr
			// return("<" + inStr.substring(0, maxSize-1) + termStr);
			return("<" + inStr.substring((len - maxSize)+1, len) + termStr);
		}
		
		if (termStr.startsWith(" ")) {// pad on left with spaces
			return(Utils.replicate(" ", maxSize - inStr.length()) + inStr + termStr);			
		}
		return inStr + termStr;
	}
	
	public String toString(ResultSet rs, ColFormat col, String colTerm) 
			throws SQLException {
		
		String outStr = "";
		
		switch (col.colType) {
		case NUMBER: // Numbers are right justified strings
			outStr = rightString(rs.getString(col.rsColNum), col.dispSize,colTerm);
			break;
			
		case MONEY:	// Money is formatted, right justified
			outStr = rightString(toMoney(rs.getBigDecimal(col.rsColNum)), col.dispSize, colTerm);
			break;

		case DATETIME:	// Datetime is formatted and right justified
			outStr = rightString(toDateTime(rs.getDate(col.rsColNum), rs.getTime(col.rsColNum))
					, col.dispSize, colTerm);
			break;
			
		case DATE:	// Time is formatted and right justified
			outStr = rightString(toDateTime(rs.getDate(col.rsColNum)), col.dispSize, colTerm);
			break;
			
		case TIME:	// Time is formatted and right justified
			outStr = rightString(toDateTime(rs.getTime(col.rsColNum)), col.dispSize, colTerm);
			break;
			
		case STRING:	// Everything else is left justified string					
		default:
			outStr = leftString(rs.getString(col.rsColNum), col.dispSize, colTerm);
			break;
		}
		
		return outStr;
	}
}

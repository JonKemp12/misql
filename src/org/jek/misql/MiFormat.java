/**
 * 
 */
package org.jek.misql;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.util.Formatter;

/**
 * @author jek
 *
 */
public class MiFormat {

	private StringBuilder sb;
	private Formatter formatter;

	public MiFormat(PrintStream out) {
		sb = new StringBuilder(20);
		formatter = new Formatter(sb);
	}

	public String toMoney(BigDecimal value) {
		sb.delete(0, sb.length());
		if (value == null)
			return "null";
		
		if (value.compareTo(BigDecimal.ZERO) == 0)
			return "0.00";
			
		formatter.format("%,.2f", value);												
		return sb.toString();
	}

	public String toDateTime(Date date) {
		sb.delete(0, sb.length());
		
		if (date == null)
			return "null";
		
		formatter.format("%1$tb %1$te %1$tY %1$tI:%1$tM%1$Tp", date);
		return sb.toString();
	}

	public String toDateTime(Date date, Time time) {
		sb.delete(0, sb.length());
		
		if (date == null)
			return "null";
		
		formatter.format("%1$tb %1$te %1$tY %2$tI:%2$tM%2$Tp", date, time);
		return sb.toString();
	}
	
	

}

package decodepcode;

import java.util.Date;

public class SQLobject implements PeopleToolsObject 
{
	String recName, sql, lastChangedBy, source;
	Date lastChanged;
	
	public SQLobject( String _recName, String _sql, String _lastChangedBy, Date _lastChanged)
	{
		recName = _recName; sql = _sql; lastChangedBy = _lastChangedBy; lastChanged = _lastChanged;
	}
	
	public String[] getKeys() 
	{
		String[] a = new String[1];
		a[0] = recName;
		return a;
	}

	public int getPeopleCodeType() {
		return -1;
	}

	public String getLastChangedBy() {
		return lastChangedBy;
	}

	public Date getLastChangedDtTm() {
		return lastChanged;
	}

	public String getRecName() {
		return recName;
	}

	public String getSql() {
		return sql;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

}

package decodepcode;

import java.util.Date;

public class SQLobject implements PeopleToolsObject 
{
	String recName, sql, lastChangedBy;
	Date lastChanged;
	
	public SQLobject( String _recName, String _sql, String _lastChangedBy, Date _lastChanged)
	{
		recName = _recName; sql = _sql; lastChangedBy = _lastChangedBy; lastChanged = _lastChanged;
	}
	
	@Override
	public String[] getKeys() 
	{
		String[] a = new String[1];
		a[0] = recName;
		return a;
	}

	@Override
	public int getPeopleCodeType() {
		return -1;
	}

	@Override
	public String getLastChangedBy() {
		return lastChangedBy;
	}

	@Override
	public Date getLastChangedDtTm() {
		return lastChanged;
	}

	public String getRecName() {
		return recName;
	}

	public String getSql() {
		return sql;
	}

}

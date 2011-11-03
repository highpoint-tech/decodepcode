package decodepcode;

import java.io.File;
import java.io.IOException;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class DirTreePTmapper implements PToolsObjectToFileMapper {
	File rootDir;
	public DirTreePTmapper( ) {}

	public DirTreePTmapper( File _rootDir)
	{
		rootDir = _rootDir;
	}
	public File getFile(PeopleToolsObject obj, String extension)
			throws IOException 
	{
		String pcType = JDBCPeopleCodeContainer.objectTypeStr(obj.getPeopleCodeType());
		File f = new File(rootDir, pcType);
		int last = -1;
		for (int i = 0; i < obj.getKeys().length; i++)
			if (obj.getKeys()[i] != null && obj.getKeys()[i].trim().length() > 0)
				last = i;
		for (int i = 0; i < last; i++)
			f = new File(f, obj.getKeys()[i].trim());
		f.mkdirs();
		f = new File(f, obj.getKeys()[last].trim() + "." + extension);
		return f;
	}
	
	public File getFileForSQL(SQLobject sqlObject, String extension) throws IOException {
		File f = new File(rootDir, "SQL");
		for ( int i = 0; i < sqlObject.getKeys().length; i++) // optional MARKET and/or DBTYPE
			f = new File(f, sqlObject.getKeys()[i]); 
		f.mkdirs();
		return new File(f, sqlObject.getKeys()[0]+ "." + extension);
	}
	public String getPath(PeopleToolsObject obj, String extension) {
		String pcType = JDBCPeopleCodeContainer.objectTypeStr(obj.getPeopleCodeType());
		String f = "/" + pcType + "/";
		int last = -1;
		for (int i = 0; i < obj.getKeys().length; i++)
			if (obj.getKeys()[i] != null && obj.getKeys()[i].trim().length() > 0)
				last = i;
		for (int i = 0; i <= last; i++)
			f += obj.getKeys()[i].trim() + "/";
		f += obj.getKeys()[last].trim() + "." + extension;
		return f;
	}
	public String getPathForSQL(SQLobject sqlObject, String extension) {
		String f = "/SQL";
		for ( int i = 0; i < sqlObject.getKeys().length; i++) // optional MARKET and/or DBTYPE
			f = f + "/" + sqlObject.getKeys()[i]; 
		return f + "/" + sqlObject.getKeys()[0]+ "." + extension;
	}

}

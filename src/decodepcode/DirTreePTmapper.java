package decodepcode;

import java.io.File;
import java.io.IOException;

public class DirTreePTmapper implements PToolsObjectToFileMapper {
	File rootDir;
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
	
	public File getFileForSQL(String recordName, String extension) throws IOException {
		recordName = recordName.trim();
		File dir = new File(new File(rootDir, "SQL"), recordName);
		dir.mkdirs();
		return new File(dir, recordName + "." + extension);
	}

}

package decodepcode;

import java.io.File;
import java.io.IOException;

/**
 * 
 * Assign a file location where to put or expect a PeopleTools object 
 *
 */
public abstract interface PToolsObjectToFileMapper {

	public abstract File getFile( PeopleToolsObject obj, String extension) throws IOException; 
	public abstract File getFileForSQL( String recordName, String extension) throws IOException;

}

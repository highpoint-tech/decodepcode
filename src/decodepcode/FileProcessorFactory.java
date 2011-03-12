package decodepcode;

import java.io.File;
import java.util.Properties;

import decodepcode.Controller.WriteDecodedPPCtoDirectoryTree;

public class FileProcessorFactory implements ContainerProcessorFactory 
{
	public ContainerProcessor getContainerProcessor() 
	{
		return new WriteDecodedPPCtoDirectoryTree(getMapper(), "pcode");	
	}

	public void setParameters(Properties properties) {}

	public PToolsObjectToFileMapper getMapper() 
	{
			return new DirTreePTmapper( new File(".", "output"));
	}
}

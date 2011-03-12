package decodepcode;

import java.io.File;
import java.util.Properties;

import decodepcode.Controller.WriteDecodedPPCtoDirectoryTree;

public class FileProcessorFactory implements ContainerProcessorFactory 
{
	@Override
	public ContainerProcessor getContainerProcessor() 
	{
		return new WriteDecodedPPCtoDirectoryTree(getMapper(), "pcode");	
	}

	@Override
	public void setParameters(Properties properties) {}

	@Override
	public PToolsObjectToFileMapper getMapper() 
	{
			return new DirTreePTmapper( new File(".", "output"));
	}
}

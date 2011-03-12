package decodepcode;

import java.util.Properties;

public interface ContainerProcessorFactory 
{
	public void setParameters( Properties properties);
	public ContainerProcessor getContainerProcessor();
	public PToolsObjectToFileMapper getMapper();
}

package decodepcode;

import java.io.IOException;

public interface ContainerProcessor
{
	void process( PeopleCodeContainer c) throws IOException;
	void processSQL( SQLobject sql) throws IOException;
}

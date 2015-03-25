package decodepcode;

public interface VersionControlSystem 
{
	public boolean existsInRepository( PeopleToolsObject obj);
	public VersionControlSystem getAncestor();
}

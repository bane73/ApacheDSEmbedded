package foo;

import java.io.File;
import java.util.HashSet;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.ldap.LdapService;
import org.apache.directory.server.protocol.shared.SocketAcceptor;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.name.LdapDN;

public class ApacheLDAPServer {

	private LdapService ldapService;
	private DirectoryService directoryService;
	private Partition partition;
	int port = 9389;

	private static ApacheLDAPServer instance;

	public static ApacheLDAPServer getInstance() {
		if (instance == null) {
			instance = new ApacheLDAPServer();
		}
		return instance;
	}

	private ApacheLDAPServer() {
	}

	public void start() throws Exception {
		directoryService = new DefaultDirectoryService();
		directoryService.setAllowAnonymousAccess(true);
		directoryService.setAccessControlEnabled(false);
		// directoryService.setExitVmOnShutdown(false);
		directoryService.setShutdownHookEnabled(false);
		directoryService.getChangeLog().setEnabled(false);

		File workdir = new File(System.getProperty("java.io.tmpdir"),
				"apache-ds");
		FileUtils.deleteDirectory(workdir);
		System.out.println("the work directory had been purged");
		workdir.mkdirs();
		directoryService.setWorkingDirectory(workdir);

		partition = addPartition("testPartition", "c=US");
		addIndex("objectClass", "ou", "uid", "cn");

		SocketAcceptor socketAcceptor = new SocketAcceptor(null);

		ldapService = new LdapService();
		ldapService.setIpPort(port);
		ldapService.setSocketAcceptor(socketAcceptor);
		ldapService.setDirectoryService(directoryService);

		directoryService.startup();
		ldapService.start();
	}

	public void applyLdif(File ldifFile) throws Exception {
		new LdifFileLoader(directoryService.getAdminSession(), ldifFile, null)
				.execute();
	}

	public void stop() throws Exception {
		ldapService.stop();
		directoryService.shutdown();
	}

	/**
	 * Add a new partition to the server
	 * 
	 * @param partitionId
	 *            The partition Id
	 * @param partitionDn
	 *            The partition DN
	 * @return The newly added partition
	 * @throws Exception
	 *             If the partition can't be added
	 */
	private Partition addPartition(String partitionId, String partitionDn)
			throws Exception {
		Partition partition = new JdbmPartition();
		partition.setId(partitionId);
		partition.setSuffix(partitionDn);
		directoryService.addPartition(partition);

		return partition;
	}

	/**
	 * Add a new set of index on the given attributes
	 * 
	 * @param partition
	 *            The partition on which we want to add index
	 * @param attrs
	 *            The list of attributes to index
	 */
	public void addIndex(String... attrs) {
		HashSet<Index<?, ServerEntry>> indexedAttributes = new HashSet<Index<?, ServerEntry>>();

		for (String attribute : attrs) {
			indexedAttributes
					.add(new JdbmIndex<String, ServerEntry>(attribute));
		}

		((JdbmPartition) partition).setIndexedAttributes(indexedAttributes);
	}

	public int getPort() {
		return port;
	}

	public static void main(String[] args) throws Exception {

		ApacheLDAPServer instance = ApacheLDAPServer.getInstance();

		System.out.println("Starting the server...");
		instance.start();

		System.out.println("Importing the LDIF...");
		instance.applyLdif(new File("data.ldif"));

		System.out
				.println("Looking up an entry that uses custom object-classes and custom attributes...");
		Entry e = instance.directoryService.getAdminSession().lookup(
				new LdapDN("cn=Custom Person,c=US"));
		System.out.println(e);

		System.out.println("Stopping the server...");
		instance.stop();
		System.out.println("Server stopped.");

		System.exit(0);
	}

}

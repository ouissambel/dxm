package org.jahia.tools.contentgenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jahia.tools.contentgenerator.bo.GroupBO;
import org.jahia.tools.contentgenerator.bo.UserBO;
import org.jahia.tools.contentgenerator.properties.ContentGeneratorCst;
import org.jdom.Document;
import org.jdom.Element;

public class UserGroupService {

	private static final Logger logger = Logger.getLogger(UserGroupService.class.getName());

	private static String sep = System.getProperty("file.separator");;

	public Document createUsersRepository(List<UserBO> users) {
		String jcrDate = ContentGeneratorService.getInstance().getDateForJcrImport(null);

		Document doc = new Document();
		Element contentNode = new Element("content");
		doc.setRootElement(contentNode);

		Element usersNode = new Element("users");
		ContentGeneratorService.getInstance().addJcrAttributes(usersNode, jcrDate);
		contentNode.addContent(usersNode);

		UserBO rootUser = new UserBO("root", hashPassword("root"), jcrDate, null);
		Element rootUserNode = rootUser.getJcrXml();
		usersNode.addContent(rootUserNode);

		for (Iterator<UserBO> iterator = users.iterator(); iterator.hasNext();) {
			UserBO userBO = iterator.next();
			usersNode.addContent(userBO.getJcrXml());
		}

		return doc;
	}

	public File createFileTreeForUsers(List<UserBO> users, File tempDirectory) throws IOException {
		File f = new File(tempDirectory + sep + "content" + sep + "users");
		FileUtils.forceMkdir(f);

		File dirUser;
		for (Iterator<UserBO> iterator = users.iterator(); iterator.hasNext();) {
			UserBO userBO = iterator.next();
			logger.debug("Creates directories tree for user " + userBO.getName());
			dirUser = new File(f + sep + userBO.getDirectoryName(1) + sep + userBO.getDirectoryName(2) + sep
					+ userBO.getDirectoryName(3) + sep + userBO.getName() + sep + "files" + sep + "profiles" + sep
					+ "publisher.png");
			FileUtils.forceMkdir(dirUser);
		}
		return f;
	}

	public Element generateJcrGroups(String siteKey, List<GroupBO> groups) {

		String jcrDate = ContentGeneratorService.getInstance().getDateForJcrImport(null);

		logger.info("Users and groups generated, creation of JCR document...");
		Element groupsNode = new Element("groups");
		groupsNode = ContentGeneratorService.getInstance().addJcrAttributes(groupsNode, jcrDate);

		// site-administrators node
		Element siteAdminNode = new Element("site-administrators");
		siteAdminNode.setAttribute("mixinTypes", "jmix:systemNode", ContentGeneratorCst.NS_JCR);
		siteAdminNode.setAttribute("primaryType", "jnt:group", ContentGeneratorCst.NS_JCR);
		groupsNode.addContent(siteAdminNode);

		Element jmembersSiteAdmin = new Element("members", ContentGeneratorCst.NS_J);
		jmembersSiteAdmin.setAttribute("primaryType", "jnt:member", ContentGeneratorCst.NS_JCR);
		siteAdminNode.addContent(jmembersSiteAdmin);

		Element rootUser = new Element("root");
		rootUser.setAttribute("member", "/users/root", ContentGeneratorCst.NS_J);
		rootUser.setAttribute("primaryType", "jnt:member", ContentGeneratorCst.NS_JNT);
		jmembersSiteAdmin.addContent(rootUser);

		// site-privileged node
		Element sitePrivilegedNode = new Element("site-privileged");
		sitePrivilegedNode = ContentGeneratorService.getInstance().addJcrAttributes(sitePrivilegedNode, jcrDate);
		sitePrivilegedNode.setAttribute("mixinTypes", "systemNode", ContentGeneratorCst.NS_JMIX);
		sitePrivilegedNode.setAttribute("primaryType", "jnt:group", ContentGeneratorCst.NS_JNT);
		groupsNode.addContent(sitePrivilegedNode);

		Element jmembersSitePrivileged = new Element("members", ContentGeneratorCst.NS_J);
		jmembersSitePrivileged.setAttribute("primaryType", "jnt:member", ContentGeneratorCst.NS_JCR);
		sitePrivilegedNode.addContent(jmembersSitePrivileged);

		Element siteAdminGroup = new Element("site-administrators___2");
		siteAdminGroup.setAttribute("member", "/sites/" + siteKey + "/groups/site-administrators",
				ContentGeneratorCst.NS_J);
		siteAdminGroup.setAttribute("primaryType", "jnt:member", ContentGeneratorCst.NS_JCR);
		jmembersSitePrivileged.setContent(siteAdminGroup);

		for (Iterator<GroupBO> iterator = groups.iterator(); iterator.hasNext();) {
			GroupBO group = iterator.next();

			Element groupNode = group.getJcrXml();
			groupsNode.addContent(groupNode);
		}
		return groupsNode;
	}

	public List<GroupBO> generateUsersAndGroups(Integer nbUsers, Integer nbGroups) {
		logger.info(nbUsers + " users and " + nbGroups + " groups are going to be generated");

		List<GroupBO> groups = new ArrayList<GroupBO>();
		String jcrDate = ContentGeneratorService.getInstance().getDateForJcrImport(null);
		logger.debug("JCR Date = " + jcrDate);

		// 1 - getNbUsersPerGroups
		Integer nbUsersPerGroup = getNbUsersPerGroup(nbUsers, nbGroups);

		// 2 - getNbusersLastGroup
		Integer nbUsersLastGroup = getNbUsersLastGroup(nbUsers, nbGroups);

		// 3 cptGroup = 1
		int cptGroups = 1;
		int cptUsersTotal = 1;
		while (cptGroups <= nbGroups.intValue()) {
			// is this the last group?
			if (cptGroups == nbGroups.intValue()) {
				nbUsersPerGroup = nbUsersLastGroup;
			}

			List<UserBO> users = new ArrayList<UserBO>();
			int cptUsers = 1;
			while (cptUsers <= nbUsersPerGroup.intValue()) {
				String dateJcr = ContentGeneratorService.getInstance().getDateForJcrImport(null);
				String username = "user" + cptUsersTotal;
				String pathJcr = getPathForUsername(username);
				// password == userName
				UserBO user = new UserBO(username, hashPassword(username), dateJcr, pathJcr);
				users.add(user);
				cptUsers++;
				cptUsersTotal++;
			}

			GroupBO group = new GroupBO("group" + cptGroups, users, jcrDate);
			groups.add(group);
			cptGroups++;
		}
		return groups;
	}

	public String getPathForUsername(String username) {

		StringBuilder builder = new StringBuilder();

		int userNameHashcode = Math.abs(username.hashCode());
		String firstFolder = getFolderName(userNameHashcode).toLowerCase();
		userNameHashcode = Math.round(userNameHashcode / 100);
		String secondFolder = getFolderName(userNameHashcode).toLowerCase();
		userNameHashcode = Math.round(userNameHashcode / 100);
		String thirdFolder = getFolderName(userNameHashcode).toLowerCase();
		return builder.append("/users").append("/").append(firstFolder).append("/").append(secondFolder).append("/")
				.append(thirdFolder).append("/").append(username).toString();
	}

	private String getFolderName(int userNameHashcode) {
		int i = (userNameHashcode % 100);
		return Character.toString((char) ('a' + Math.round(i / 10))) + Character.toString((char) ('a' + (i % 10)));
	}

	public Integer getNbUsersPerGroup(Integer nbUsers, Integer nbGroups) {
		Integer nbUsersPerGroup = nbUsers / nbGroups;
		return nbUsersPerGroup;
	}

	public Integer getNbUsersLastGroup(Integer nbUsers, Integer nbGroups) {
		Integer nbUsersLastGroup = nbUsers % nbGroups;
		if (nbUsersLastGroup.equals(Integer.valueOf(0))) {
			nbUsersLastGroup = getNbUsersPerGroup(nbUsers, nbGroups);
		}
		return nbUsersLastGroup;
	}

	public String hashPassword(String password) {
		// TODO: make a real hash when method will be know. For now, we return
		// "guillaume"
		return "W6ph5Mm5Pz8GgiULbPgzG37mj9g";
	}
}

/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate;

import java.sql.SQLException;
import java.util.Hashtable;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

/**
 * This combined LDAP authentication method supersedes both the 'LDAPAuthentication'
 * and the 'LDAPHierarchicalAuthentication' methods. It's capable of both:
 * - authenticaton  against a flat LDAP tree where all users are in the same unit
 *   (if search.user or search.password is not set)
 * - authentication against structured hierarchical LDAP trees of users. 
 *   An initial bind is required using a user name and password in order to
 *   search the tree and find the DN of the user. A second bind is then required to
 *   check the credentials of the user by binding directly to their DN.
 *
 * @author Stuart Lewis, Chris Yates, Alex Barbieri, Flavio Botelho, Reuben Pasquini, Samuel Ottenhoff, Ivan Masár
 * @version $Revision$
 */
public class LDAPAuthentication
    implements AuthenticationMethod {

    /** log4j category */
    private static Logger log = Logger.getLogger(LDAPAuthentication.class);

    /**
     * Let a real auth method return true if it wants.
     */
    public boolean canSelfRegister(Context context,
                                   HttpServletRequest request,
                                   String username)
        throws SQLException
    {
        // Looks to see if autoregister is set or not
        return ConfigurationManager.getBooleanProperty("authentication-ldap", "autoregister");
    }

    /**
     *  Nothing here, initialization is done when auto-registering.
     */
    public void initEPerson(Context context, HttpServletRequest request,
            EPerson eperson)
        throws SQLException
    {
        // XXX should we try to initialize netid based on email addr,
        // XXX  for eperson created by some other method??
    }

    /**
     * Cannot change LDAP password through dspace, right?
     */
    public boolean allowSetPassword(Context context,
                                    HttpServletRequest request,
                                    String username)
        throws SQLException
    {
        // XXX is this right?
        return false;
    }

    /*
     * This is an explicit method.
     */
    public boolean isImplicit()
    {
        return false;
    }

    /*
     * Add authenticated users to the group defined in dspace.cfg by
     * the login.specialgroup key.
     */
    public int[] getSpecialGroups(Context context, HttpServletRequest request)
    {
        // Prevents anonymous users from being added to this group, and the second check
        // ensures they are LDAP users
        try
        {
            if (!context.getCurrentUser().getNetid().equals(""))
            {
                String groupName = ConfigurationManager.getProperty("authentication-ldap", "login.specialgroup");
                if ((groupName != null) && (!groupName.trim().equals("")))
                {
                    Group ldapGroup = Group.findByName(context, groupName);
                    if (ldapGroup == null)
                    {
                        // Oops - the group isn't there.
                        log.warn(LogManager.getHeader(context,
                                "ldap_specialgroup",
                                "Group defined in login.specialgroup does not exist"));
                        return new int[0];
                    } else
                    {
                        return new int[] { ldapGroup.getID() };
                    }
                }
            }
        }
        catch (Exception npe) {
            // The user is not an LDAP user, so we don't need to worry about them
        }
        return new int[0];
    }

    /*
     * Authenticate the given credentials.
     * This is the heart of the authentication method: test the
     * credentials for authenticity, and if accepted, attempt to match
     * (or optionally, create) an <code>EPerson</code>.  If an <code>EPerson</code> is found it is
     * set in the <code>Context</code> that was passed.
     *
     * @param context
     *  DSpace context, will be modified (ePerson set) upon success.
     *
     * @param username
     *  Username (or email address) when method is explicit. Use null for
     *  implicit method.
     *
     * @param password
     *  Password for explicit auth, or null for implicit method.
     *
     * @param realm
     *  Realm is an extra parameter used by some authentication methods, leave null if
     *  not applicable.
     *
     * @param request
     *  The HTTP request that started this operation, or null if not applicable.
     *
     * @return One of:
     *   SUCCESS, BAD_CREDENTIALS, CERT_REQUIRED, NO_SUCH_USER, BAD_ARGS
     * <p>Meaning:
     * <br>SUCCESS         - authenticated OK.
     * <br>BAD_CREDENTIALS - user exists, but credentials (e.g. passwd) don't match
     * <br>CERT_REQUIRED   - not allowed to login this way without X.509 cert.
     * <br>NO_SUCH_USER    - user not found using this method.
     * <br>BAD_ARGS        - user/pw not appropriate for this method
     */
    public int authenticate(Context context,
                            String netid,
                            String password,
                            String realm,
                            HttpServletRequest request)
        throws SQLException
    {
        log.info(LogManager.getHeader(context, "auth", "attempting trivial auth of user="+netid));

        // Skip out when no netid or password is given.
        if (netid == null || password == null)
        {
            return BAD_ARGS;
        }

        // Locate the eperson
        EPerson eperson = null;
        try
        {
                eperson = EPerson.findByNetid(context, netid.toLowerCase());
        }
        catch (SQLException e)
        {
        }
        SpeakerToLDAP ldap = new SpeakerToLDAP(log);

        // Get the DN of the user
        boolean anonymousSearch = ConfigurationManager.getBooleanProperty("authentication-ldap", "search.anonymous");
        String adminUser = ConfigurationManager.getProperty("authentication-ldap", "search.user");
        String adminPassword = ConfigurationManager.getProperty("authentication-ldap", "search.password");
        String objectContext = ConfigurationManager.getProperty("authentication-ldap", "object_context");
        String idField = ConfigurationManager.getProperty("authentication-ldap", "id_field");
        String dn = "";

        // If adminUser is blank and anonymous search is not allowed, then we can't search so construct the DN instead of searching it
        if ((StringUtils.isBlank(adminUser) || StringUtils.isBlank(adminPassword)) && !anonymousSearch)
        {
            dn = idField + "=" + netid + "," + objectContext;
        }
        else
        {
            dn = ldap.getDNOfUser(adminUser, adminPassword, context, netid);
        }

        // Check a DN was found
        if ((dn == null) || (dn.trim().equals("")))
        {
            log.info(LogManager
                .getHeader(context, "failed_login", "no DN found for user " + netid));
            return BAD_CREDENTIALS;
        }

        // if they entered a netid that matches an eperson
        if (eperson != null)
        {
            // e-mail address corresponds to active account
            if (eperson.getRequireCertificate())
            {
                return CERT_REQUIRED;
            }
            else if (!eperson.canLogIn())
            {
                return BAD_ARGS;
            }

            if (ldap.ldapAuthenticate(dn, password, context))
            {
                context.setCurrentUser(eperson);

                // assign user to groups based on ldap dn
                assignGroups(dn, ldap.ldapGroup, context);
                
                log.info(LogManager
                    .getHeader(context, "authenticate", "type=ldap"));
                return SUCCESS;
            }
            else
            {
                return BAD_CREDENTIALS;
            }
        }
        else
        {
            // the user does not already exist so try and authenticate them
            // with ldap and create an eperson for them

            if (ldap.ldapAuthenticate(dn, password, context))
            {
                // Register the new user automatically
                log.info(LogManager.getHeader(context,
                                "autoregister", "netid=" + netid));

                String email = ldap.ldapEmail;

                // Check if we were able to determine an email address from LDAP
                if (StringUtils.isEmpty(email))
                {
                    // If no email, check if we have a "netid_email_domain". If so, append it to the netid to create email
                    if (StringUtils.isNotEmpty(ConfigurationManager.getProperty("authentication-ldap", "netid_email_domain")))
                    {
                        email = netid + ConfigurationManager.getProperty("authentication-ldap", "netid_email_domain");
                    }
                    else
                    {
                        // We don't have a valid email address. We'll default it to 'netid' but log a warning
                        log.warn(LogManager.getHeader(context, "autoregister",
                                "Unable to locate email address for account '" + netid + "', so it has been set to '" + netid + "'. " +
                                "Please check the LDAP 'email_field' OR consider configuring 'netid_email_domain'."));
                        email = netid;
                    }
                }

                if (StringUtils.isNotEmpty(email))
                {
                    try
                    {
                        eperson = EPerson.findByEmail(context, email);
                        if (eperson!=null)
                        {
                            log.info(LogManager.getHeader(context,
                                    "type=ldap-login", "type=ldap_but_already_email"));
                            context.turnOffAuthorisationSystem();
                            eperson.setNetid(netid.toLowerCase());
                            eperson.update();
                            context.commit();
                            context.restoreAuthSystemState();
                            context.setCurrentUser(eperson);

                            // assign user to groups based on ldap dn
                            assignGroups(dn, ldap.ldapGroup, context);

                            return SUCCESS;
                        }
                        else
                        {
                            if (canSelfRegister(context, request, netid))
                            {
                                // TEMPORARILY turn off authorisation
                                try
                                {
                                    context.turnOffAuthorisationSystem();
                                    eperson = EPerson.create(context);
                                    if (StringUtils.isNotEmpty(email))
                                    {
                                        eperson.setEmail(email);
                                    }
                                    if (StringUtils.isNotEmpty(ldap.ldapGivenName))
                                    {
                                        eperson.setFirstName(ldap.ldapGivenName);
                                    }
                                    if (StringUtils.isNotEmpty(ldap.ldapSurname))
                                    {
                                        eperson.setLastName(ldap.ldapSurname);
                                    }
                                    if (StringUtils.isNotEmpty(ldap.ldapPhone))                                    
                                    {
                                        eperson.setMetadata("phone", ldap.ldapPhone);
                                    }
                                    eperson.setNetid(netid.toLowerCase());
                                    eperson.setCanLogIn(true);
                                    AuthenticationManager.initEPerson(context, request, eperson);
                                    eperson.update();
                                    context.commit();
                                    context.setCurrentUser(eperson);

                                    // assign user to groups based on ldap dn
                                    assignGroups(dn, ldap.ldapGroup, context);
                                }
                                catch (AuthorizeException e)
                                {
                                    return NO_SUCH_USER;
                                }
                                finally
                                {
                                    context.restoreAuthSystemState();
                                }

                                log.info(LogManager.getHeader(context, "authenticate",
                                            "type=ldap-login, created ePerson"));
                                return SUCCESS;
                            }
                            else
                            {
                                // No auto-registration for valid certs
                                log.info(LogManager.getHeader(context,
                                                "failed_login", "type=ldap_but_no_record"));
                                return NO_SUCH_USER;
                            }
                        }
                    }
                    catch (AuthorizeException e)
                    {
                        eperson = null;
                    }
                    finally
                    {
                        context.restoreAuthSystemState();
                    }
                }
            }
        }
        return BAD_ARGS;
    }

    /**
     * Internal class to manage LDAP query and results, mainly
     * because there are multiple values to return.
     */
    private static class SpeakerToLDAP {

        private Logger log = null;

        protected String ldapEmail = null;
        protected String ldapGivenName = null;
        protected String ldapSurname = null;
        protected String ldapPhone = null;
        protected String ldapGroup = null;

        /** LDAP settings */
        String ldap_provider_url = ConfigurationManager.getProperty("authentication-ldap", "provider_url");
        String ldap_id_field = ConfigurationManager.getProperty("authentication-ldap", "id_field");
        String ldap_search_context = ConfigurationManager.getProperty("authentication-ldap", "search_context");
        String ldap_search_scope = ConfigurationManager.getProperty("authentication-ldap", "search_scope");

        String ldap_email_field = ConfigurationManager.getProperty("authentication-ldap", "email_field");
        String ldap_givenname_field = ConfigurationManager.getProperty("authentication-ldap", "givenname_field");
        String ldap_surname_field = ConfigurationManager.getProperty("authentication-ldap", "surname_field");
        String ldap_phone_field = ConfigurationManager.getProperty("authentication-ldap", "phone_field");
        String ldap_group_field = ConfigurationManager.getProperty("authentication-ldap", "login.groupmap.attribute"); 

        SpeakerToLDAP(Logger thelog)
        {
            log = thelog;
        }

        protected String getDNOfUser(String adminUser, String adminPassword, Context context, String netid) {
    String resultDN = null;

    // Парсим search_scope
    int ldap_search_scope_value = 0;
    try {
        ldap_search_scope_value = Integer.parseInt(ldap_search_scope.trim());
    } catch (NumberFormatException e) {
        if (ldap_search_scope != null) {
            log.warn(LogManager.getHeader(context, "ldap_authentication",
                    "Invalid search scope: " + ldap_search_scope));
        }
    }

    // Настройка окружения для InitialDirContext
    Hashtable<String, String> env = new Hashtable<>();
    env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(javax.naming.Context.PROVIDER_URL, ldap_provider_url);

    if (adminUser != null && !adminUser.trim().isEmpty() &&
        adminPassword != null && !adminPassword.trim().isEmpty()) {
        env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
        env.put(javax.naming.Context.SECURITY_PRINCIPAL, adminUser);
        env.put(javax.naming.Context.SECURITY_CREDENTIALS, adminPassword);
        log.debug(LogManager.getHeader(context, "LDAP Bind", 
                "Using admin credentials: " + adminUser));
    } else {
        env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "none");
        log.debug(LogManager.getHeader(context, "LDAP Bind", "Using anonymous bind"));
    }

    DirContext ctx = null;
    try {
        ctx = new InitialDirContext(env);
        log.debug(LogManager.getHeader(context, "LDAP Context", "Successfully created InitialDirContext"));

        // Настройка поиска
        SearchControls ctrls = new SearchControls();
        ctrls.setSearchScope(ldap_search_scope_value);

        String searchFilter = "(&(" + ldap_id_field + "=" + netid + "))";
        log.debug(LogManager.getHeader(context, "LDAP Search", 
                "Searching for netid=" + netid + " with filter=" + searchFilter 
                + " in context=" + ldap_search_context));

        NamingEnumeration<SearchResult> answer = ctx.search(ldap_search_context, searchFilter, ctrls);

        while (answer.hasMoreElements()) {
            SearchResult sr = answer.next();
            resultDN = sr.getNameInNamespace(); // полный DN
            log.debug(LogManager.getHeader(context, "LDAP DN calculated",
                    "netid=" + netid + ", DN=" + resultDN));

            // Получаем атрибуты
            Attributes atts = sr.getAttributes();
            ldapEmail = getAttrValue(atts, ldap_email_field);
            ldapGivenName = getAttrValue(atts, ldap_givenname_field);
            ldapSurname = getAttrValue(atts, ldap_surname_field);
            ldapPhone = getAttrValue(atts, ldap_phone_field);
            ldapGroup = getAttrValue(atts, ldap_group_field);

            log.debug(LogManager.getHeader(context, "LDAP Attributes",
                    "netid=" + netid
                    + ", email=" + ldapEmail
                    + ", givenName=" + ldapGivenName
                    + ", surname=" + ldapSurname
                    + ", phone=" + ldapPhone
                    + ", group=" + ldapGroup));

            return resultDN; // берем первый результат
        }

        // Если результата нет
        log.warn(LogManager.getHeader(context, "LDAP Search", "No DN found for netid=" + netid));

    } catch (NamingException e) {
        log.warn(LogManager.getHeader(context, "ldap_authentication",
                "LDAP search failed for netid=" + netid + ": " + e));
    } finally {
        try {
            if (ctx != null) ctx.close();
            log.debug(LogManager.getHeader(context, "LDAP Context", "LDAP context closed"));
        } catch (NamingException ignored) {}
    }

    return null;
}

/**
 * Вспомогательный метод для безопасного получения значения атрибута LDAP
 */
private String getAttrValue(Attributes attrs, String attrName) throws NamingException {
    if (attrName != null && attrs != null) {
        Attribute attr = attrs.get(attrName);
        if (attr != null) {
            Object val = attr.get();
            return val != null ? val.toString() : null;
        }
    }
    return null;
}


        /**
         * contact the ldap server and attempt to authenticate
         */
        protected boolean ldapAuthenticate(String netid, String password, Context context) {
    if (password == null || password.isEmpty()) return false;

    // Получаем DN пользователя через admin
    String adminUser = ConfigurationManager.getProperty("authentication-ldap", "search.user");
    String adminPassword = ConfigurationManager.getProperty("authentication-ldap", "search.password");
    String userDN = getDNOfUser(adminUser, adminPassword, context, netid);

    if (userDN == null) {
        log.warn(LogManager.getHeader(context, "ldap_authentication",
                "Cannot determine DN for netid=" + netid));
        return false;
    }

    log.debug(LogManager.getHeader(context, "LDAP Authenticate",
            "Attempting bind with DN=" + userDN + " for netid=" + netid));

    Hashtable<String, String> env = new Hashtable<>();
    env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(javax.naming.Context.PROVIDER_URL, ldap_provider_url);
    env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
    env.put(javax.naming.Context.SECURITY_PRINCIPAL, userDN);
    env.put(javax.naming.Context.SECURITY_CREDENTIALS, password);
    env.put(javax.naming.Context.AUTHORITATIVE, "true");
    env.put(javax.naming.Context.REFERRAL, "follow");

    DirContext ctx = null;
    try {
        ctx = new InitialDirContext(env);
        return true;
    } catch (NamingException e) {
        log.warn(LogManager.getHeader(context, "ldap_authentication",
                "LDAP bind failed for netid=" + netid + " DN=" + userDN + ": " + e));
        return false;
    } finally {
        try { if (ctx != null) ctx.close(); } catch (NamingException ignored) {}
    }
}
    }

    /*
     * Returns URL to which to redirect to obtain credentials (either password
     * prompt or e.g. HTTPS port for client cert.); null means no redirect.
     *
     * @param context
     *  DSpace context, will be modified (ePerson set) upon success.
     *
     * @param request
     *  The HTTP request that started this operation, or null if not applicable.
     *
     * @param response
     *  The HTTP response from the servlet method.
     *
     * @return fully-qualified URL
     */
    public String loginPageURL(Context context,
                            HttpServletRequest request,
                            HttpServletResponse response)
    {
        return response.encodeRedirectURL(request.getContextPath() +
                                          "/ldap-login");
    }

    /**
     * Returns message key for title of the "login" page, to use
     * in a menu showing the choice of multiple login methods.
     *
     * @param context
     *  DSpace context, will be modified (ePerson set) upon success.
     *
     * @return Message key to look up in i18n message catalog.
     */
    public String loginPageTitle(Context context)
    {
        return "org.dspace.eperson.LDAPAuthentication.title";
    }


    /*
     * Add authenticated users to the group defined in dspace.cfg by
     * the authentication-ldap.login.groupmap.* key.
     */
    private void assignGroups(String dn, String group, Context context)
    {
        if (StringUtils.isNotBlank(dn)) 
        {
            System.out.println("dn:" + dn);
            int i = 1;
            String groupMap = ConfigurationManager.getProperty("authentication-ldap", "login.groupmap." + i);
            
            boolean cmp;
            
            while (groupMap != null)
            {
                String t[] = groupMap.split(":");
                String ldapSearchString = t[0];
                String dspaceGroupName = t[1];
 
                if (group == null) {
                    cmp = StringUtils.containsIgnoreCase(dn, ldapSearchString + ",");
                } else {
                    cmp = StringUtils.equalsIgnoreCase(group, ldapSearchString);
                }

                if (cmp) 
                {
                    // assign user to this group   
                    try
                    {
                        Group ldapGroup = Group.findByName(context, dspaceGroupName);
                        if (ldapGroup != null)
                        {
                            ldapGroup.addMember(context.getCurrentUser());
                            ldapGroup.update();
                            context.commit();
                        }
                        else
                        {
                            // The group does not exist
                            log.warn(LogManager.getHeader(context,
                                    "ldap_assignGroupsBasedOnLdapDn",
                                    "Group defined in authentication-ldap.login.groupmap." + i + " does not exist :: " + dspaceGroupName));
                        }
                    }
                    catch (AuthorizeException ae)
                    {
                        log.debug(LogManager.getHeader(context, "assignGroupsBasedOnLdapDn could not authorize addition to group", dspaceGroupName));
                    }
                    catch (SQLException e)
                    {
                        log.debug(LogManager.getHeader(context, "assignGroupsBasedOnLdapDn could not find group", dspaceGroupName));
                    }
                }

                groupMap = ConfigurationManager.getProperty("authentication-ldap", "login.groupmap." + ++i);
            }
        }
    }
}

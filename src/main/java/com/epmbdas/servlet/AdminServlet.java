package com.epmbdas.servlet;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.user.EntityException;
import com.atlassian.user.GroupManager;
import com.epmbdas.ao.AffectedGroups;
import com.epmbdas.ao.Emails;
import com.epmbdas.ao.PluginData;
import com.epmbdas.ao.PublicSpaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atlassian.templaterenderer.TemplateRenderer;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import static com.google.common.base.Preconditions.checkNotNull;

@Scanned
public class AdminServlet extends HttpServlet implements PluginData{
    public static final Pattern VALID_EMAIL_ADDRESS_REGEX = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
    //private static final Logger log = LoggerFactory.getLogger(AdminServlet.class);
    @ComponentImport
    private final ActiveObjects ao;
    @ComponentImport
    private final TemplateRenderer renderer;
    @ComponentImport
    private final SpaceManager spaceManager;
    @ComponentImport
    private final GroupManager groupManager;
    @ComponentImport
    private final UserManager userManager;
    @ComponentImport
    private final LoginUriProvider loginUriProvider;

    @Inject
    public AdminServlet(UserManager userManager, LoginUriProvider loginUriProvider, TemplateRenderer renderer, ActiveObjects ao, SpaceManager spaceManager, GroupManager groupManager)
    {
        this.userManager = userManager;
        this.loginUriProvider = loginUriProvider;
        this.renderer = renderer;
        this.ao = checkNotNull(ao); //checkNotNull method here is statically imported from the google collections’ Preconditions class.
        this.spaceManager = spaceManager;
        this.groupManager = groupManager;
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        response.sendRedirect(loginUriProvider.getLoginUri(getUri(request)).toASCIIString());
    }

    private URI getUri(HttpServletRequest request)
    {
        StringBuffer builder = request.getRequestURL();
        if (request.getQueryString() != null)
        {
            builder.append("?");
            builder.append(request.getQueryString());
        }
        return URI.create(builder.toString());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        UserProfile username = userManager.getRemoteUser(req);
        if (username == null || !userManager.isAdmin(username.getUserKey()))
        {
            redirectToLogin(req, resp);
            return;
        }
        resp.setContentType("text/html");
        Map<String, Object> context = new HashMap<>();
        context.put("publicSpaces", getPublicSpacesAsString());
        context.put("affectedGroups", getAffectedGroupsAsString());
        context.put("notificationReceivers", getNotificationReceiversAsString());
        renderer.render("admin.vm", context, resp.getWriter());
        resp.getWriter().close();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final String publicSpaceKey = req.getParameter("spacekey").trim();
        final String spaceKeyToRemove = req.getParameter("rspacekey").trim();
        final String affectedGroup = req.getParameter("group").trim();
        final String groupToRemove = req.getParameter("rgroup").trim();
        final String notificationReceiver = req.getParameter("notification").trim();
        final String notificationReceiverToRemove = req.getParameter("rnotification").trim();

        //add space key to DB
        if (spaceManager.getSpace(publicSpaceKey) != null && !getPublicSpacesFromAO(ao).contains(publicSpaceKey) && !publicSpaceKey.equals(spaceKeyToRemove)) {
            ao.executeInTransaction(new TransactionCallback<PublicSpaces>() {
                @Override
                public PublicSpaces doInTransaction() {
                    final PublicSpaces publicSpaces = ao.create(PublicSpaces.class);
                    publicSpaces.setPublicSpaceKey(publicSpaceKey);
                    publicSpaces.save();
                    return publicSpaces;
                 }
             });
        }

        //remove space key
        if (getPublicSpacesFromAO(ao).contains(spaceKeyToRemove) && !spaceKeyToRemove.equals(publicSpaceKey)) {
            ao.executeInTransaction(new TransactionCallback<PublicSpaces>() {
                @Override
                public PublicSpaces doInTransaction() {
                    final PublicSpaces publicSpaces = ao.create(PublicSpaces.class);
                    for (PublicSpaces ps : ao.find(PublicSpaces.class, "PUBLIC_SPACE_KEY = ?", spaceKeyToRemove)) {
                        try {
                            ps.getEntityManager().delete(ps);
                        } catch (SQLException e) {
                                e.printStackTrace();
                            }
                    }
                    publicSpaces.save();
                    return publicSpaces;
                }
            });
        }

        //add affected group
        try {
            if (groupManager.getGroup(affectedGroup) != null && !affectedGroup.equals(groupToRemove) && !getGroupsFromAO(ao).contains(affectedGroup)) {
                ao.executeInTransaction(new TransactionCallback<AffectedGroups>() {
                    @Override
                    public AffectedGroups doInTransaction() {
                        final AffectedGroups affectedGroups = ao.create(AffectedGroups.class);
                        affectedGroups.setAffectedGroup(affectedGroup);
                        affectedGroups.save();
                        return affectedGroups;
                    }
                });
            }
        } catch (EntityException e) {
            e.printStackTrace();
        }

        //remove group
        if (!groupToRemove.equals(affectedGroup) && getGroupsFromAO(ao).contains(groupToRemove)) {
            ao.executeInTransaction(new TransactionCallback<AffectedGroups>() {
                @Override
                public AffectedGroups doInTransaction() {
                    final AffectedGroups affectedGroups = ao.create(AffectedGroups.class);
                    for (AffectedGroups ag : ao.find(AffectedGroups.class, "AFFECTED_GROUP = ?", groupToRemove)) {
                        try {
                            ag.getEntityManager().delete(ag);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    affectedGroups.save();
                    return affectedGroups;
                }
            });
        }

        //add notification receiver
        if (notificationReceiver != null && validateEmail(notificationReceiver) && !getEmailsFromAO(ao).contains(notificationReceiver) && !notificationReceiver.equals(notificationReceiverToRemove)) {
            ao.executeInTransaction(new TransactionCallback<Emails>() {
                @Override
                public Emails doInTransaction() {
                    final Emails nr = ao.create(Emails.class);
                    nr.setEmail(notificationReceiver);
                    nr.save();
                    return nr;
                }
            });
        }

        //remove notification receiver
        if (notificationReceiverToRemove != null && getEmailsFromAO(ao).contains(notificationReceiverToRemove) && !notificationReceiverToRemove.equals(notificationReceiver)) {
            ao.executeInTransaction(new TransactionCallback<Emails>() {
                @Override
                public Emails doInTransaction() {
                    final Emails notificationReceivers = ao.create(Emails.class);
                    for (Emails nr : ao.find(Emails.class, "EMAIL = ?", notificationReceiverToRemove)) {
                        try {
                            nr.getEntityManager().delete(nr);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    notificationReceivers.save();
                    return notificationReceivers;
                }
            });
        }
        Map<String, Object> context = new HashMap<>();
        context.put("publicSpaces", getPublicSpacesAsString());
        context.put("affectedGroups", getAffectedGroupsAsString());
        context.put("notificationReceivers", getNotificationReceiversAsString());
        renderer.render("admin.vm", context, resp.getWriter());
        resp.getWriter().close();
    }

    private String getAffectedGroupsAsString() {
        String [] result = {""};
        Set<String> groups = getGroupsFromAO(ao);
        if (groups.size() < 1) {
            return "No Affected Groups";
        }
        else {
            groups.forEach(s -> result[0] += s + ", ");
            return result[0].substring(0, result[0].length()-2);
        }
    }

    private String getPublicSpacesAsString() {
        String [] result = {""};
        Set<String> spaces = getPublicSpacesFromAO(ao);
        if (spaces.size() < 1) {
            return "No Public Spaces";
        }
        else {
            spaces.forEach(s -> result[0] += s + ", ");
            return result[0].substring(0, result[0].length()-2);
        }
    }

    private String getNotificationReceiversAsString() {
        String [] result = {""};
        Set<String> emails = getEmailsFromAO(ao);
        if (emails.size() < 1) {
            return "No Notification Receivers";
        }
        else {
            emails.forEach(s -> result[0] += s + ", ");
            return result[0].substring(0, result[0].length()-2);
        }
    }

    public static boolean validateEmail(String emailStr) {
        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX .matcher(emailStr);
        return matcher.find();
    }
}
package com.epmbdas.job;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.security.SpacePermission;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.scheduler.JobRunner;
import com.atlassian.scheduler.JobRunnerRequest;
import com.atlassian.scheduler.JobRunnerResponse;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.epmbdas.ao.PluginData;
import com.epmbdas.mail.ResultNotification;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
public class CleanJob implements JobRunner, PluginData {
    @ComponentImport
    private final TransactionTemplate transactionTemplate;
    @ComponentImport
    private final SpaceManager spaceManager;
    @ComponentImport
    private final ActiveObjects ao;

    @Autowired
    public CleanJob(TransactionTemplate transactionTemplate, SpaceManager spaceManager, ActiveObjects ao) {
        this.transactionTemplate = transactionTemplate;
        this.spaceManager = spaceManager;
        this.ao = checkNotNull(ao);
    }

    private String cleanPermissions() {
        List<Space> all = spaceManager.getAllSpaces();
        Set<String> publicSpaces = getPublicSpacesFromAO(ao);
        Set<String> groups = getGroupsFromAO(ao);

        StringBuilder stringBuilder = new StringBuilder();

        if (publicSpaces.size() > 0 && groups.size() > 0) {
            all.forEach(space -> {
                if (!publicSpaces.contains(space.getKey())) {
                    List<SpacePermission> spacePermissionsToRemove = new ArrayList<>(space.getPermissions());
                    spacePermissionsToRemove.forEach(spacePermission -> {
                        if (spacePermission.isGroupPermission() && groups.contains(spacePermission.getGroup())) {

                            stringBuilder.append("Space: ");
                            stringBuilder.append(spacePermission.getSpace().getKey());
                            stringBuilder.append(" | ");
                            stringBuilder.append("Group: ");
                            stringBuilder.append(spacePermission.getGroup());
                            stringBuilder.append(" | ");
                            stringBuilder.append("Permission: ");
                            stringBuilder.append(spacePermission.getType());
                            stringBuilder.append(" | ");
                            stringBuilder.append("Violator: ");
                            try {
                                stringBuilder.append(spacePermission.getCreator().getName()); //null pointer exception with ds space
                            } catch (NullPointerException e) {
                                stringBuilder.append("null");
                            }
                            stringBuilder.append(" | ");
                            stringBuilder.append(" Date: ");
                            stringBuilder.append(spacePermission.getCreationDate());
                            stringBuilder.append("\n");
                            spacePermission.getSpace().removePermission(spacePermission);
                        }
                    });
                }
            });

        }
        return stringBuilder.toString();
    }

    @Override
    public JobRunnerResponse runJob(JobRunnerRequest request) {
        if (request.isCancellationRequested()) {
            return JobRunnerResponse.aborted("Job cancelled.");
        }
        transactionTemplate.execute(new TransactionCallback(){
            @Override
            public Void doInTransaction() {

                String notificationBody = cleanPermissions();
                Set<String> emails = getEmailsFromAO(ao);
                if (notificationBody.toString().length() > 0 && emails.size() > 0) {
                    emails.forEach(email -> {
                        ResultNotification notification = new ResultNotification();
                        notification.sendEmail(email, "Confluence Security Issue", notificationBody);
                    });
                }
                    return null;
            }
        });
        return JobRunnerResponse.success("Job finished successfully.");
    }
}




package com.epmbdas.ao;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.transaction.TransactionCallback;
import java.util.HashSet;
import java.util.Set;

public interface PluginData {

    default Set<String> getPublicSpacesFromAO(ActiveObjects ao){
        Set<String> spaces = new HashSet<>();
        ao.executeInTransaction(new TransactionCallback<Void>()
        {
            @Override
            public Void doInTransaction()
            {
                for (PublicSpaces publicSpace : ao.find(PublicSpaces.class))
                {
                    if (publicSpace.getPublicSpaceKey() != null)  spaces.add(publicSpace.getPublicSpaceKey());
                }
                return null;
            }
        });
        return spaces;
    }

    default Set<String> getGroupsFromAO(ActiveObjects ao) {
        Set<String> groups = new HashSet<>();
        ao.executeInTransaction(new TransactionCallback<Void>() // (1)
        {
            @Override
            public Void doInTransaction()
            {
                for (AffectedGroups affectedGroups : ao.find(AffectedGroups.class))
                {
                    if (affectedGroups.getAffectedGroup() != null)  groups.add(affectedGroups.getAffectedGroup());
                }
                return null;
            }
        });
        return groups;
    }

    default Set<String> getEmailsFromAO(ActiveObjects ao) {
        Set<String> notificationReceivers = new HashSet<>();
        ao.executeInTransaction(new TransactionCallback<Void>() // (1)
        {
            @Override
            public Void doInTransaction()
            {
                for (Emails nr : ao.find(Emails.class))
                {
                    if (nr.getEmail() != null)  notificationReceivers.add(nr.getEmail());
                }
                return null;
            }
        });
        return notificationReceivers;
    }
}

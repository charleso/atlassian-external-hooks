package com.ngs.stash.externalhooks.hook;

import com.atlassian.stash.hook.*;
import com.atlassian.stash.hook.repository.*;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.setting.*;
import com.atlassian.stash.env.SystemProperties;
import java.util.Collection;
import java.io.*;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedList;


public class ExternalPostReceiveHook implements AsyncPostReceiveRepositoryHook, RepositorySettingsValidator
{
    /**
     * Call external executable as git hook.
     */
    @Override
    public void postReceive(RepositoryHookContext context, Collection<RefChange> refChanges)
    {
        String repo_path = System.getProperty(SystemProperties.HOME_DIR_SYSTEM_PROPERTY) +
            "/data/repositories/" + context.getRepository().getId();

        List<String> exe = new LinkedList<String>();
        exe.add(context.getSettings().getString("exe"));
        if (context.getSettings().getString("params") != null) {
            for (String arg : context.getSettings().getString("params").split("\r\n")) {
                exe.add(arg);
            }
        }

        ProcessBuilder pb = new ProcessBuilder(exe);
        pb.directory(new File(repo_path));
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            InputStream input = process.getInputStream();
            OutputStream output = process.getOutputStream();

            for (RefChange refChange : refChanges) {
                output.write((refChange.getFromHash() + " " +
                    refChange.getToHash() + " " +
                    refChange.getRefId() + "\n").getBytes("UTF-8"));
            }
            output.close();

            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository)
    {
        if (settings.getString("exe", "").isEmpty())
        {
            errors.addFieldError("exe", "Executable is blank, please specify something");
        }
    }
}

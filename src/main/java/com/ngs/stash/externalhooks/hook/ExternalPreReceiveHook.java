package com.ngs.stash.externalhooks.hook;

import com.atlassian.stash.hook.*;
import com.atlassian.stash.hook.repository.*;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.setting.*;
import com.atlassian.stash.env.SystemProperties;
import com.atlassian.stash.user.StashAuthenticationContext;
import java.util.Collection;
import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedList;

public class ExternalPreReceiveHook implements PreReceiveRepositoryHook, RepositorySettingsValidator
{
    private StashAuthenticationContext authCtx;
    public ExternalPreReceiveHook(StashAuthenticationContext authenticationContext) {
        authCtx = authenticationContext;
    }

    /**
     * Call external executable as git hook.
     */
    @Override
    public boolean onReceive(RepositoryHookContext context, Collection<RefChange> refChanges, HookResponse hookResponse)
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
        Map<String, String> env = pb.environment();
        env.put("STASH_USER_NAME", authCtx.getCurrentUser().getName());
        env.put("STASH_USER_EMAIL", authCtx.getCurrentUser().getEmailAddress());
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

            if (hookResponse != null) {
                int data;
                while ((data = input.read()) >= 0) {
                    hookResponse.err().print(Character.toString((char)data));
                    hookResponse.err().flush();
                }

            }

            return process.waitFor() == 0;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
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

package org.jenkinsci.plugins.liquibase.evaluator;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.exception.LiquibaseException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import org.jenkinsci.plugins.liquibase.common.LiquibaseProperty;
import org.jenkinsci.plugins.liquibase.exception.LiquibaseRuntimeException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Build step that invoke's liquibase's rollback against a target database.
 */
public class RollbackBuildStep extends AbstractLiquibaseBuilder {
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    public static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    private String rollbackType;
    protected String numberOfChangesetsToRollback;
    private String rollbackLastHours;
    private String rollbackToTag;
    private String rollbackToDate;


    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_PATTERN);

    public enum RollbackStrategy {
        TAG, DATE, RELATIVE, COUNT
    }

    public RollbackBuildStep() {


    }

    @DataBoundConstructor
    public RollbackBuildStep(String databaseEngine,
                             String changeLogFile,
                             String username,
                             String password,
                             String url,
                             String defaultSchemaName,
                             String contexts,
                             String liquibasePropertiesPath,
                             String classpath,
                             String driverClassname,
                             String changeLogParameters,
                             String labels,
                             String basePath,
                             String rollbackType,
                             String numberOfChangesetsToRollback,
                             String rollbackLastHours,
                             String rollbackToTag, String rollbackToDate, boolean useIncludedDriver) {
        super(databaseEngine, changeLogFile, username, password, url, defaultSchemaName, contexts,
                liquibasePropertiesPath,
                classpath, driverClassname, changeLogParameters, labels, basePath, useIncludedDriver);

        this.rollbackType = rollbackType;
        this.numberOfChangesetsToRollback = numberOfChangesetsToRollback;
        this.rollbackLastHours = rollbackLastHours;
        this.rollbackToTag = rollbackToTag;
        this.rollbackToDate = rollbackToDate;
    }

    @Override
    public void doPerform(AbstractBuild<?, ?> build,
                          BuildListener listener,
                          Liquibase liquibase,
                          Contexts contexts,
                          ExecutedChangesetAction executedChangesetAction, Properties configProperties)
            throws InterruptedException, IOException, LiquibaseException {

        executedChangesetAction.setRollbackOnly(true);
        RolledbackChangesetAction action = new RolledbackChangesetAction(build);
        RollbackStrategy rollbackStrategy = RollbackStrategy.valueOf(rollbackType);
        build.addAction(action);

        EnvVars environment = build.getEnvironment(listener);
        LabelExpression labelExpression = new LabelExpression(getProperty(configProperties, LiquibaseProperty.LABELS));

        if (rollbackStrategy == RollbackStrategy.COUNT) {
            String resolvedRollbackCount = Util.replaceMacro(numberOfChangesetsToRollback, environment);
            int rollbackCount;
            if (resolvedRollbackCount != null) {
                rollbackCount = Integer.parseInt(resolvedRollbackCount);
            } else {
                throw new LiquibaseRuntimeException(
                        "Invalid value '" + numberOfChangesetsToRollback + "' for rollback count.");
            }
            listener.getLogger().println("Rollback back the last " + rollbackCount + " changeset(s) applied.");
            liquibase.rollback(rollbackCount, contexts, labelExpression);
        }

        if (rollbackStrategy == RollbackStrategy.DATE || rollbackStrategy == RollbackStrategy.RELATIVE) {
            Date targetDate = resolveTargetDate(rollbackStrategy, environment);
            listener.getLogger()
                    .println("Rolling back changeset(s) applied after date " + simpleDateFormat.format(targetDate));
            liquibase.rollback(targetDate, contexts, labelExpression);
        }

        if (rollbackStrategy == RollbackStrategy.TAG) {
            String resolvedTag = Util.replaceMacro(rollbackToTag, environment);
            listener.getLogger().println("Rolling back database to tag '" + resolvedTag + "'");
            liquibase.rollback(resolvedTag, contexts, labelExpression);
        }

        action.setRolledbackChangesets(executedChangesetAction.getRolledBackChangesets());
    }


    protected Date resolveTargetDate(RollbackStrategy rollbackStrategy, EnvVars environment) {
        Date now = new Date();
        return resolveTargetDate(rollbackStrategy, now, environment);
    }

    protected Date resolveTargetDate(RollbackStrategy rollbackStrategy, Date now, EnvVars environment) {
        Date targetDate;
        if (rollbackStrategy == RollbackStrategy.RELATIVE) {
            Calendar instance = Calendar.getInstance();
            instance.setTime(now);

            String lastHours = Util.replaceMacro(rollbackLastHours, environment);
            if (lastHours != null) {
                instance.add(Calendar.HOUR, 0 - Integer.parseInt(lastHours));
            }
            targetDate = instance.getTime();
        } else {
            String rollbackDate = Util.replaceMacro(rollbackToDate, environment);
            try {
                targetDate = simpleDateFormat.parse(rollbackDate);
            } catch (ParseException e) {
                throw new LiquibaseRuntimeException("Invalid value for rollback to date value:" + rollbackDate,
                        e);
            }
        }
        return targetDate;
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public String getNumberOfChangesetsToRollback() {
        return numberOfChangesetsToRollback;
    }

    @DataBoundSetter
    public void setNumberOfChangesetsToRollback(String numberOfChangesetsToRollback) {
        this.numberOfChangesetsToRollback = numberOfChangesetsToRollback;
    }

    public String getRollbackToTag() {
        return rollbackToTag;
    }

    @DataBoundSetter
    public void setRollbackToTag(String rollbackToTag) {
        this.rollbackToTag = rollbackToTag;
    }

    public String getRollbackToDate() {
        return rollbackToDate;
    }

    @DataBoundSetter
    public void setRollbackToDate(String rollbackToDate) {
        this.rollbackToDate = rollbackToDate;
    }

    public String getRollbackType() {
        return rollbackType;
    }

    @DataBoundSetter
    public void setRollbackType(String rollbackType) {
        this.rollbackType = rollbackType;
    }


    public String getRollbackLastHours() {
        return rollbackLastHours;
    }

    @DataBoundSetter
    public void setRollbackLastHours(String rollbackLastHours) {
        this.rollbackLastHours = rollbackLastHours;
    }

    public static class DescriptorImpl extends AbstractLiquibaseDescriptor {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Roll back liquibase changes";
        }

    }

}

package com.viettel.schedule;

import com.viettel.event.LeaderChangedEvent;
import com.viettel.repository.CommonRepository;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Slf4j
@Component
public class ScheduleService {

   @Autowired
   private CommonRepository commonRepository;

   @Autowired
   private ScheduleTask scheduleTask;

   @Autowired
   private LeaderElectionService leaderEclectionService;



   @EventListener
   @Async
   public void changeLeader( LeaderChangedEvent event) throws SchedulerException {
       Scheduler scheduler = new StdSchedulerFactory().getScheduler();
       if(scheduler!= null) {
           schedule.clear();
       }
       if(leaderEclectionService.isLeader()) {
           log.info("leader changed!, reload all schedule");
           commonRepository.updateScheduleJobName();
           runOne();
       }
   }

   @Scheduled(cron = "${cron-schedule}")
   public void runOne() throws SchedulerException {
       if(!leaderEclectionService.isLeader()) {
           log.debug("instance is not leader");
           return ;
       }
       log.info("Schedule print log");

       // get all current schedule
       List<ScheduleQuery> ls = commonRepository.getScheduleIsActive();
       List<ScheduleQuery> currentJob = new ArrayList<>();


   }


   public void stopSchedule(String jobName) throws SchedulerException {
    Scheduler scheduler = new StdSchedulerFactory().getScheduler();
    for(String groupName : scheduler.getJobGroupNames()) {
        for(JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
            if(jobKey.getName().equals(jobName)) {
                log.info("delete job {} {}", jobName, scheduler.deleteJob(jobKey) ? "succecced": "failed");
            }
        }
    }
   }




   public String starttNewJobSchedule(
    String jobName,
    String groupName,
    String cronString,
    ScheduleQuery scheduleQuery,
    ScheduleTask scheduleTask
   ) {
    String jn = null;
    try {
        JobDataMap m = new JobDataMap();
        m.put(INPUT_DATA, scheduleQuery.getId());
        pm.put("schedule_task", scheduleTask);
        m.put("common_repisiroty", commonRepository);
        JobDetail job = JobBuilder.newJob(ScheduleJob.class)
        .withIdentity(jobName, groupName)
        .usingJobData(m)
        .build();
        CronTrigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("cron_" + jobName, groupName)
        .withSchedule(CronScheduleBuilder.cronSchedule(cronString))
        .forJob(jobName, groupName)
        .startNow()
        .build();
    }catch(Exception ex) {
        log.error("Error: {}", ex.getMessage());
    }
    return jn;
   }



}

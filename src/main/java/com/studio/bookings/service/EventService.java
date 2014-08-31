package com.studio.bookings.service;

import static org.joda.time.DateTimeConstants.MONDAY;
import static org.joda.time.DateTimeConstants.SUNDAY;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Months;
import org.joda.time.Seconds;
import org.joda.time.Weeks;
import org.joda.time.Years;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.Named;
import com.googlecode.objectify.NotFoundException;
import com.studio.bookings.entity.Account;
import com.studio.bookings.entity.Calendar;
import com.studio.bookings.entity.Event;
import com.studio.bookings.entity.EventAttribute;
import com.studio.bookings.entity.EventCategory;
import com.studio.bookings.entity.EventItem;
import com.studio.bookings.entity.Person;
import com.studio.bookings.enums.EventRepeatType;
import com.studio.bookings.util.Constants;

@Api(
	    name = "booking",
	    version = "v1",
	    scopes = {Constants.EMAIL_SCOPE},
	    clientIds = {Constants.WEB_CLIENT_ID},
	    audiences = {Constants.ANDROID_AUDIENCE}
	)

public class EventService extends BaseService {
	
	public static CalendarService calendarService = new CalendarService();

	@ApiMethod(name = "calendar.insertEvent", path="calendar/{calendar_id}/event",  httpMethod = HttpMethod.POST)
	public Event insertEvent(
			@Named("account_id") Long accountId,
			@Named("calendar_id") Long calendarId,
			@Named("repeatEvent") String repeatEvent,
			@Named("eventRepeatType") String eventRepeatType,
			@Named("eventRepeatInterval") Integer repeatInterval,
			@Named("finalRepeatDate") Long finalRepeatDate,
			@Named("eventRepeatCount") Integer eventRepeatCount,
			@Named("repeatDaysOfWeek") Integer[] repeatDaysOfWeek,
			@Named("excludeDays") String[] excludeDays,
			@Named("title") String title,
			@Named("startDateTime") Long startDateTime,
			@Named("endDateTime") Long endDateTime,
			@Named("allDay") String allDay,
			@Named("maxAttendees") Integer maxAttendees,
			@Named("instructor_id") Long instructorId, 
			@Named("eventCategory") Long eventCategory,
			@Named("eventAttribute") Long eventAttribute
			) throws ParseException, NotFoundException {
				
		// Get Account
		Account account = accountDao.retrieve(accountId);
		
		
		// retrieving Calendar	
		Calendar calendar = calendarDao.retrieveAncestor(calendarId, account);
		
		// Set Event Item Category
		EventCategory eventCategoryFetched =  eventCategoryDao.retrieveAncestor(eventCategory, account);			
		EventAttribute eventAttributeFetched =  eventAttributeDao.retrieveAncestor(eventAttribute, account);
		
		//Format Dates
		DateFormat formatter = new SimpleDateFormat("HH:mm dd MM yyyy");
		Date eventStart = new Date();
		eventStart.setTime(startDateTime);
		
		Date eventEnd = new Date();
		eventEnd.setTime(endDateTime);
		
		Boolean allDayBoolean = new Boolean(allDay);
		
		// Set Instructor
		Person instructor = personDao.retrieveAncestor(instructorId, account);
				
		// Setup Repeat Event
		Boolean repeatBoolean = new Boolean(repeatEvent);
		EventRepeatType repeatType = null;
		List<Integer> daysOfWeek = new ArrayList<Integer>();
		List<Date> repeatExcludeDays = new ArrayList<Date>();
		Integer repeatCount = 0;
		Date eventFinalRepeatDate = null;
		
		if (repeatBoolean) {
			repeatType = EventRepeatType.valueOf(eventRepeatType);
			daysOfWeek = Arrays.asList(repeatDaysOfWeek);
			repeatCount = eventRepeatCount;
			eventFinalRepeatDate = new Date();
			eventFinalRepeatDate.setTime(finalRepeatDate);
			for (int i = 0; i < excludeDays.length; i++) {
				repeatExcludeDays.add(new DateTime(formatter.parse(excludeDays[i])).toDate());
			}
		}

		Event event = new Event(
				calendar, repeatBoolean, repeatType, repeatInterval, 
				eventFinalRepeatDate, repeatCount, daysOfWeek, repeatExcludeDays, 
				title, eventStart, eventEnd, allDayBoolean, maxAttendees, 
				instructor, eventCategoryFetched, eventAttributeFetched);
		
		eventDao.save(event);    
		return event;
	}
	
 
	@ApiMethod(name = "calendar.listEvents", path="calendar/{calendar_id}/event",  httpMethod = HttpMethod.GET)
	public List<EventItem> listEvents(
			@Named("account_id") Long accountId,
			@Named("calendar_id") Long calendarId,
			@Named("date_range_start") Long dateStart) throws ParseException {
		
		// Get Account
		Account account = accountDao.retrieve(accountId);
		
		// Get Calendar
		Calendar calendar = calendarDao.retrieveAncestor(calendarId, account);
		
		//Format Dates
		Date eventStart = new Date();
		eventStart.setTime(dateStart);
		//eventStart = new DateTime(eventStart).minusMonths(6).toDate();
		Date dateEnd = new DateTime(eventStart).plusMonths(6).toDate();
		
		List<EventItem> allEvents = new ArrayList<EventItem>();
		
		List<Event> events = eventDao.dateRangeAncestorQuery("startDateTime >=", eventStart, "startDateTime <", dateEnd, calendar);

        Date currentDate = null;
        
        for (Event event : events) {
	        if (event.getRepeatEvent()) {
	            currentDate = findNextOccurrence(event, eventStart);
	            int duration = Seconds.secondsBetween(new DateTime(event.getStartDateTime()), 
	            		new DateTime(event.getEndDateTime())).getSeconds();
	            
	            while (currentDate!= null && currentDate.before(dateEnd)) {
	            	Date endDate = new DateTime(currentDate).plusSeconds(duration).toDate();
	            	EventItem recurrEvent = new EventItem(event.getId(), currentDate, endDate, event);

	            	allEvents.add(recurrEvent);
	                Date nextMinute = new DateTime(currentDate).plusMinutes(1).toDate();
	                currentDate = findNextOccurrence(event, nextMinute);
	            }
	        }
	        // One time (non-recurring) event
	        else {
	        	EventItem singleEvent = new EventItem(event.getId(), event.getStartDateTime(), event.getEndDateTime(), event);
	        	allEvents.add(singleEvent);
	        }
        }
		
		return allEvents;
	}

	
	private Date findNextOccurrence(Event event, Date afterDate) {
        Date nextOccurrence = null;

        if (!event.getRepeatEvent()) {
            // non-repeating event
            nextOccurrence = null;
        } else if (event.getRepeatEvent() && afterDate.after(event.getRepeatFinalDate())) {
            // Event is already over
            nextOccurrence = null;
        } else if (afterDate.before(event.getStartDateTime())) {
            // First occurrence
            if (event.getRepeatType() == EventRepeatType.WEEKLY && !(isOnRecurringDay(event, event.getStartDateTime()))) {
                Date nextDay = new DateTime(event.getStartDateTime()).plusDays(1).toDate();
                nextOccurrence = findNextOccurrence(event, nextDay);
            }
            else {
                nextOccurrence = event.getStartDateTime();
            }
        } else {
            switch (event.getRepeatType()) {
                case DAILY:
                    nextOccurrence = findNextDailyOccurrence(event, afterDate);
                    break;
                case WEEKLY:
                    nextOccurrence = findNextWeeklyOccurrence(event, afterDate);
                    break;
                case MONTHLY:
                    nextOccurrence = findNextMonthlyOccurrence(event, afterDate);
                    break;
                case YEARLY:
                    nextOccurrence = findNextYearlyOccurrence(event, afterDate);
                    break;
                default:
                	nextOccurrence = null;
                	break;
                	
            }
        }

        if (isOnExcludedDay(event, nextOccurrence)) {
            // Skip this occurrence and go to the next one
            nextOccurrence = findNextOccurrence(event, nextOccurrence);
        }
        else if (event.getRepeatFinalDate() != null && nextOccurrence != null) {
        	if (event.getRepeatFinalDate().before(nextOccurrence) || event.getRepeatFinalDate().equals(nextOccurrence)) {
        		// Next occurrence happens after recurUntil date
        		nextOccurrence = null;
        	}
        }

        return nextOccurrence;
    }

    private Date findNextDailyOccurrence(Event event, Date afterDate) {
        DateTime nextOccurrence = new DateTime(event.getStartDateTime());

        int daysBeforeDate = Days.daysBetween(new DateTime(event.getStartDateTime()), new DateTime(afterDate)).getDays();

        double occurrencesBeforeDate = Math.floor(daysBeforeDate / event.getRepeatInterval());

        double daysToAdd = (occurrencesBeforeDate + 1) * event.getRepeatInterval();

        nextOccurrence = nextOccurrence.plusDays((int) daysToAdd);

        return nextOccurrence.toDate();
    }


    private Date findNextWeeklyOccurrence(Event event, Date afterDate) {
    	
    	// weeks between event start date and date after last event
        int weeksBeforeDate = Weeks.weeksBetween(new DateTime(event.getStartDateTime()), new DateTime(afterDate)).getWeeks();
       
        // number of occurrences in the weeks between event start date and date after last event
        int weekOccurrencesBeforeDate = (int) Math.floor(weeksBeforeDate / event.getRepeatInterval());

        // original start date
        DateTime lastOccurrence = new DateTime(event.getStartDateTime());
        // start date plus weeks of events so far
        lastOccurrence = lastOccurrence.plusWeeks(weekOccurrencesBeforeDate * event.getRepeatInterval());
        // gets the Monday date in the week of the last occurrence
        lastOccurrence = lastOccurrence.withDayOfWeek(MONDAY);

        DateTime nextOccurrence;
        
        // if last occurrence and date after last event are in same week, set next occurrence to one day after Monday
        // of that week to check for any repeatDaysOfWeek
        if (isInSameWeek(lastOccurrence.toDate(), afterDate)) {
            nextOccurrence = lastOccurrence.plusDays(1);
        }
        // if not in same in same week add a weekly repeat interval on top of last event
        else {
            nextOccurrence = lastOccurrence.plusWeeks(event.getRepeatInterval());
        }

        boolean occurrenceFound = false;

        while (!occurrenceFound) {
            if (nextOccurrence.toDate().after(afterDate) && isOnRecurringDay(event, nextOccurrence.toDate())) {
                occurrenceFound = true;
            }
            else {
                if (nextOccurrence.getDayOfWeek() == SUNDAY) {
                    // we're about to pass into the next week
                    nextOccurrence = nextOccurrence.withDayOfWeek(MONDAY).plusWeeks(event.getRepeatInterval());
                }
                else {
                    nextOccurrence = nextOccurrence.plusDays(1);
                }
            }
        }
        return nextOccurrence.toDate();
    }

    private Date findNextMonthlyOccurrence(Event event, Date afterDate) {
        DateTime nextOccurrence = new DateTime(event.getStartDateTime());

        int monthsBeforeDate = Months.monthsBetween(new DateTime(event.getStartDateTime()), new DateTime(afterDate)).getMonths();
        int occurrencesBeforeDate = (int) Math.floor(monthsBeforeDate / event.getRepeatInterval());
        nextOccurrence = nextOccurrence.plusMonths((occurrencesBeforeDate + 1) * event.getRepeatInterval());

        return nextOccurrence.toDate();
    }

    private Date findNextYearlyOccurrence(Event event, Date afterDate) {
        DateTime nextOccurrence = new DateTime(event.getStartDateTime());

        int yearsBeforeDate = Years.yearsBetween(new DateTime(event.getStartDateTime()), new DateTime(afterDate)).getYears();
        int occurrencesBeforeDate = (int) Math.floor(yearsBeforeDate / event.getRepeatInterval());
        nextOccurrence = nextOccurrence.plusYears((occurrencesBeforeDate + 1) * event.getRepeatInterval());

        return nextOccurrence.toDate();
    }


    private boolean isInSameWeek(Date date1, Date date2) {
        DateTime dateTime1 = new DateTime(date1);
        DateTime dateTime2 = new DateTime(date2);

        return ((Weeks.weeksBetween(dateTime1, dateTime2)).getWeeks() == 0);
    }

    private boolean isOnSameDay(Date date1, Date date2) {
        DateTime dateTime1 = new DateTime(date1);
        DateTime dateTime2 = new DateTime(date2);

       return ((Days.daysBetween(dateTime1, dateTime2)).getDays() == 0);
    }

    private boolean isOnRecurringDay(Event event, Date date) {
        int day = new DateTime(date).getDayOfWeek();
        List<Integer> repeatDays = event.getRepeatDaysOfWeek();
        if (repeatDays == null) {
        	return false;
        }
        return repeatDays.contains(day);
    }

    private boolean isOnExcludedDay(Event event, Date date) {
        date = (new DateTime(date)).withTime(0, 0, 0, 0).toDate();
        
        List<Date> excludeDays = event.getExcludeDays();
        if (excludeDays == null) {
        	return false;
        }
        return excludeDays.contains(date);
    }
    
    //http://www.craigburke.com/2012/02/18/google-calendar-in-grails-part3-creating-and-modifying-events.html
    // Final repeat date based on count
    /*
    // Set recurUntil date based on the recurCount value
    if (recurCount && !recurUntil) {
       Date recurCountDate = startTime

       for (int i in 1..recurCount) {
           recurCountDate = eventService.findNextOccurrence(this, new DateTime(recurCountDate).plusMinutes(1).toDate())
       }

       recurUntil = new DateTime(recurCountDate).plusMinutes(durationMinutes).toDate()
    }*/
    
    
    
    
    
    
    
    
    
    // Update Delete Event
    
    /*def updateEvent(Event eventInstance, String editType, def params) {
        def result = [:]

        try {
            if (!eventInstance) {
                result = [error: 'not.found']
            }
            else if (!eventInstance.isRecurring) {
                eventInstance.properties = params

                if (eventInstance.hasErrors() || !eventInstance.save(flush: true)) {
                    result = [error: 'has.errors']
                }
            }
            else {
                Date startTime = params.date('startTime', ['MM/dd/yyyy hh:mm a'])
                Date endTime = params.date('endTime', ['MM/dd/yyyy hh:mm a'])

                // Using the date from the original startTime and endTime with the update time from the form
                int updatedDuration = Minutes.minutesBetween(new DateTime(startTime), new DateTime(endTime)).minutes

                Date updatedStartTime = new DateTime(eventInstance.startTime).withTime(startTime.hours, startTime.minutes, 0, 0).toDate()
                Date updatedEndTime = new DateTime(updatedStartTime).plusMinutes(updatedDuration).toDate()

                if (editType == "occurrence") {
                    // Add an exclusion
                    eventInstance.with {
                        addToExcludeDays(new DateTime(startTime).withTime(0, 0, 0, 0).toDate())
                        save(flush: true)
                    }

                    // single event
                    new Event(params).with {
                        startTime = updatedStartTime
                        endTime = updatedEndTime
                        isRecurring = false // ignore recurring options this is a single event
                        save(flush: true)
                    }
                }
                else if (editType == "following") {
                    // following event
                    new Event(params).with {
                        recurUntil = eventInstance.recurUntil
                        save(flush: true)
                    }

                    eventInstance.with {
                        recurUntil = startTime
                        save(flush: true)
                    }
                }
                else if (editType == "all") {
                    eventInstance.properties = params
                    eventInstance.startTime = updatedStartTime
                    eventInstance.endTime = updatedEndTime

                    if (eventInstance.hasErrors() || !eventInstance.save()) {
                        result = [error: 'has.errors']
                    }
                }
            }
        }
        catch (Exception ex) {
            result = [error: 'has.errors']
        }

        result
    }

    def deleteEvent(Event eventInstance, Date occurrenceStart, String deleteType) {

        def result = [:]

        try {
            if (!eventInstance) {
                result = [error: 'not.found']
            }
            if (!eventInstance.isRecurring || deleteType == "all") {
                eventInstance.delete(flush: true)
            }
            else if (eventInstance && deleteType) {
                if (deleteType == "occurrence") {
                    // Add an exclusion
                    eventInstance.addToExcludeDays(new DateTime(occurrenceStart).withTime(0, 0, 0, 0).toDate())
                    eventInstance.save(flush: true);
                }
                else if (deleteType == "following") {
                    eventInstance.recurUntil = occurrenceStart
                    eventInstance.save(flush: true)
                }
            }
        }
        catch (Exception ex) {
            result = [error: 'has.errors']
        }

        result
    }*/
}

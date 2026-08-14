package com.campushub.venue.persistence;

import com.campushub.venue.domain.Venue;
import com.campushub.venue.domain.VenueDay;
import com.campushub.venue.domain.VenueDay.Booking;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class VenueRepository {

    private final MongoTemplate mongoTemplate;

    public VenueRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String insertVenue(String name) {
        return mongoTemplate.insert(new Venue(name)).getId();
    }

    public boolean venueExists(String venueId) {
        return mongoTemplate.exists(new Query(Criteria.where("id").is(venueId)), Venue.class);
    }

    public Optional<Venue> findVenue(String venueId) {
        return Optional.ofNullable(mongoTemplate.findById(venueId, Venue.class));
    }

    public List<Venue> listVenues(int page, int size) {
        Query query = new Query()
                .with(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Venue.class);
    }

    public long countVenues() {
        return mongoTemplate.count(new Query(), Venue.class);
    }

    public boolean renameVenue(String venueId, String name) {
        Query query = new Query(Criteria.where("id").is(venueId));
        return mongoTemplate.updateFirst(query, new Update().set("name", name), Venue.class).getMatchedCount() > 0;
    }

    public Optional<VenueDay> findDay(String venueId, LocalDate date) {
        Query query = new Query(Criteria.where("venueId").is(venueId).and("date").is(date.toString()));
        return Optional.ofNullable(mongoTemplate.findOne(query, VenueDay.class));
    }

    public boolean acquire(String venueId, LocalDate date, Booking booking) {
        try {
            return acquire(venueId, date, booking, true);
        } catch (DuplicateKeyException exception) {
            // The first two bookings for a day may both race to insert. The unique (venueId, date)
            // index chooses the first winner; retry once against that now-existing document.
            return acquire(venueId, date, booking, false);
        }
    }

    private boolean acquire(String venueId, LocalDate date, Booking booking, boolean upsert) {
        Document overlap = new Document("$not", new Document("$elemMatch", new Document(
                                "startMinute", new Document("$lt", booking.endMinute()))
                        .append("endMinute", new Document("$gt", booking.startMinute()))));
        Document filter = new Document("venueId", venueId)
                .append("date", date.toString())
                .append("bookings", overlap);
        VenueDay updated = mongoTemplate.findAndModify(
                new BasicQuery(filter),
                new Update().push("bookings", booking),
                FindAndModifyOptions.options().upsert(upsert).returnNew(true),
                VenueDay.class);
        return updated != null;
    }

    public List<String> conflictingEventIds(
            String venueId, LocalDate date, int startMinute, int endMinute) {
        VenueDay day = mongoTemplate.findOne(
                new Query(Criteria.where("venueId").is(venueId).and("date").is(date.toString())), VenueDay.class);
        if (day == null) {
            return List.of();
        }
        return day.getBookings().stream()
                .filter(booking -> booking.startMinute() < endMinute && booking.endMinute() > startMinute)
                .map(Booking::eventId)
                .distinct()
                .toList();
    }

    public void release(String venueId, LocalDate date, Booking booking) {
        Query query = new Query(Criteria.where("venueId").is(venueId).and("date").is(date.toString()));
        Document exactBooking = new Document("eventId", booking.eventId())
                .append("startMinute", booking.startMinute())
                .append("endMinute", booking.endMinute());
        mongoTemplate.updateFirst(query, new Update().pull("bookings", exactBooking), VenueDay.class);
    }

    public void removeBookings(String venueId, LocalDate date, Set<String> eventIds) {
        if (eventIds.isEmpty()) {
            return;
        }
        Query query = new Query(Criteria.where("venueId").is(venueId).and("date").is(date.toString()));
        Document matchingEvents = new Document("eventId", new Document("$in", eventIds));
        mongoTemplate.updateFirst(query, new Update().pull("bookings", matchingEvents), VenueDay.class);
    }

    public void releaseEventSlots(String eventId) {
        Query query = new Query(Criteria.where("bookings.eventId").is(eventId));
        mongoTemplate.updateMulti(
                query, new Update().pull("bookings", new Document("eventId", eventId)), VenueDay.class);
    }

    public void ensureIndexes() {
        mongoTemplate
                .indexOps(VenueDay.class)
                .createIndex(new Index()
                        .on("venueId", Sort.Direction.ASC)
                        .on("date", Sort.Direction.ASC)
                        .unique()
                        .named("venueId_1_date_1"));
    }
}

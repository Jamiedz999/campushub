package com.campushub.venue.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("venues")
public class Venue {

    @Id
    private String id;

    private String name;

    public Venue() {}

    public Venue(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

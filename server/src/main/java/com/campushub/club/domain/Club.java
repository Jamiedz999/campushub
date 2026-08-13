package com.campushub.club.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("clubs")
public class Club {

    @Id
    private String id;

    private String name;

    public Club() {}

    public Club(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

package archrulesfixtures.moduleb;

import org.springframework.data.mongodb.core.mapping.Document;

// Fixture only: the "another module's document" that modulea reaches into.
@Document
public class SomeDocument {}

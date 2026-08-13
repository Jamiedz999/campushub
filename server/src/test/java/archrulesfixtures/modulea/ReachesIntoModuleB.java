package archrulesfixtures.modulea;

import archrulesfixtures.moduleb.SomeDocument;

// Fixture only: proves DocumentOwnershipRulesTest's ownership rule actually fails a real cross-module reference.
public class ReachesIntoModuleB {

    public SomeDocument document;
}

package databack.common.command;

public enum StandardDatapackOwners implements IDatapackOwner {

    /// From the datapack/ folder under a world
    World,
    /// From mod injection something into the vanilla namespace
    Builtin,
    /// From a modern version of minecraft (via tx loader)
    BuiltinModern;

    @Override
    public String getName() {
        return switch (this) {
            case World -> "file";
            case Builtin -> "builtin";
            case BuiltinModern -> "builtin";
        };
    }
}

sealed class RemoteCommand {

    object Start : RemoteCommand()
    object Stop : RemoteCommand()

    data class SetDates(val dates: List<String>) : RemoteCommand()
    object ClearDates : RemoteCommand()

    object Status : RemoteCommand()
    object Screenshot : RemoteCommand()

    object Pause : RemoteCommand()
    object Resume : RemoteCommand()

    object ForceScan : RemoteCommand()
}
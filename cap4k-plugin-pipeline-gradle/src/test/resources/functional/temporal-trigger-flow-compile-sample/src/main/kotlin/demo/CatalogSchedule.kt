package demo

import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
import org.springframework.scheduling.annotation.Scheduled

class RefreshCatalogCmd : Command<Unit>
class ReadCatalogQuery : Query<Unit>
class RefreshSearchCapability : CapabilityCall<Unit>

class CatalogSchedule(
    private val commands: CommandSupervisor,
    private val queries: QuerySupervisor,
    private val capabilities: CapabilitySupervisor,
) {
    @Scheduled
    fun refresh() {
        commands.send(RefreshCatalogCmd())
    }

    @Scheduled
    fun inspect() {
        queries.ask(ReadCatalogQuery())
        capabilities.call(RefreshSearchCapability())
    }

    fun helper() {
        commands.send(RefreshCatalogCmd())
    }
}

package pl.com.bottega.factory.demand.forecasting

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.Commit
import pl.com.bottega.factory.demand.forecasting.persistence.DemandDao
import pl.com.bottega.factory.demand.forecasting.persistence.ProductDemandDao
import spock.lang.Specification

import javax.persistence.EntityManager
import javax.transaction.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest
@Transactional
@Commit
class DemandORMRepositoryTest extends Specification {

    def clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    def events = Mock(DemandEventsMapping)
    @Autowired
    EntityManager em
    @Autowired
    ProductDemandDao rootDao
    @Autowired
    DemandDao demandDao

    DemandORMRepository repository

    final def today = LocalDate.now(clock)
    final def refNo = "3009000"

    def setup() {
        demandDao.deleteAllInBatch()
        rootDao.deleteAllInBatch()
        repository = new DemandORMRepository(clock, events, em, rootDao, demandDao)
    }

    def "persists new demand"() {
        given:
        noDemandsInDB()

        when:
        def object = demandIsLoadedFromDB()
        object.adjust(demandAdjustment(today, 2000))
        repository.save(object)

        then:
        def demandsInDB = demandDao.findAll()
        demandsInDB.size() == 1
        demandsInDB.every hasAdjustment(2000)
    }

    def "updates existing demand"() {
        given:
        demandInDB((today): 1000)

        when:
        def object = demandIsLoadedFromDB()
        object.adjust(demandAdjustment(today, 2000))
        repository.save(object)

        then:
        def demandsInDB = demandDao.findAll()
        demandsInDB.size() == 1
        demandsInDB.every hasAdjustment(2000)
    }

    def "doesn't fetch historical data"() {
        given:
        demandInDB((today.minusDays(1)): 10000, (today): 1000)

        when:
        def demands = demandDao.findByProductRefNoAndDateGreaterThanEqual(refNo, today)

        then:
        demands.size() == 1
        demands.every { it -> it.date == today }
    }

    private ProductDemandEntity noDemandsInDB() {
        rootDao.save(new ProductDemandEntity(refNo))
    }

    private void demandInDB(Map<LocalDate, Long> demands) {
        def root = rootDao.save(new ProductDemandEntity(refNo))
        demands.each { date, level ->
            def demand = new DemandEntity(root, date)
            demand.set(Demand.of(level), null)
            demandDao.save(demand)
        }
    }

    private AdjustDemand demandAdjustment(LocalDate date, long level) {
        new AdjustDemand(refNo, [
                (date): Adjustment.strong(Demand.of(level))
        ])
    }

    private ProductDemand demandIsLoadedFromDB() {
        repository.get(refNo)
    }

    private def hasAdjustment(long level) {
        return { it.get().getAdjustment() == Adjustment.strong(Demand.of(level)) }
    }
}

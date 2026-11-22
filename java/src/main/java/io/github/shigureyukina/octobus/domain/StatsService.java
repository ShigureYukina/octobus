package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.persistence.StatsRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Service for stats queries.
 */
@Service
public class StatsService {

    static final int DEFAULT_HOURS = 24;
    static final int MAX_HOURS = 24;

    private final StatsRepository statsRepository;

    public StatsService(StatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    /**
     * Returns the single total stats row.
     */
    public StatsTotal total() {
        return statsRepository.findTotal();
    }

    /**
     * Returns today's daily stats.
     */
    public StatsDaily today() {
        return statsRepository.findToday();
    }

    /**
     * Returns all daily stats records.
     */
    public List<StatsDaily> daily() {
        return statsRepository.findAllDaily();
    }

  /**
   * Returns hourly stats for the last N hours.
   */
  public List<StatsHourly> hourly(int hours) {
    if (hours <= 0 || hours > MAX_HOURS) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "hours must be between 1 and " + MAX_HOURS);
    }
    return statsRepository.findHourly(hours);
  }

  /**
   * Returns all per-API-key stats rows.
   */
  public List<StatsAPIKey> apikeyStats() {
    return statsRepository.findByApiKey();
  }
}

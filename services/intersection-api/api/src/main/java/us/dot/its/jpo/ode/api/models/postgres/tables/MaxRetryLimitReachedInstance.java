package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "max_retry_limit_reached_instances", schema = "public")
public class MaxRetryLimitReachedInstance {
  @EmbeddedId
  private MaxRetryLimitReachedInstanceId id;

  @MapsId
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rsu_id", nullable = false)
  private Rsu rsu;

}
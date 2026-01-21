package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Embeddable
public class MaxRetryLimitReachedInstanceId implements Serializable {
  @Serial
  private static final long serialVersionUID = 8990620363904200198L;
}
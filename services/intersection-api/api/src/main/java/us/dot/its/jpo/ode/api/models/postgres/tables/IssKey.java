package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "iss_keys")
public class IssKey {
    @NotNull
    @ColumnDefault("nextval('iss_keys_iss_key_id_seq')")
    @Column(name = "iss_key_id", nullable = false)
    private Integer issKeyId;

    @Size(max = 128)
    @NotNull
    @Column(name = "common_name", nullable = false, length = 128)
    private String commonName;

    @Size(max = 128)
    @NotNull
    @Column(name = "token", nullable = false, length = 128)
    private String token;


}
package us.dot.its.jpo.ode.api.models.postgres.tables;

import java.util.List;
import java.util.Objects;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

@Getter
@Setter
@Entity
@Table(name = "organizations")
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "organizations_id_gen")
    @SequenceGenerator(name = "organizations_id_gen", sequenceName = "organizations_organization_id_seq", allocationSize = 1)
    @Column(name = "organization_id", nullable = false)
    private Integer id;

    @Size(max = 128)
    @NotNull
    @Column(name = "name", nullable = false, unique = true, length = 128)
    private String name;

    @Size(max = 128)
    @Column(name = "email", length = 128)
    private String email;

    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
    private List<IntersectionOrganization> intersectionOrganizations;

    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
    private List<UserOrganization> userOrganizations;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Organization that = (Organization) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
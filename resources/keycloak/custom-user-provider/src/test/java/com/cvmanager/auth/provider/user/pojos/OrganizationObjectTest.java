package com.cvmanager.auth.provider.user.pojos;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.List;
import java.util.Map;

class OrganizationObjectTest {

    @Test
    void listFromString() {
        List<OrganizationObject> orgs = OrganizationObject.listFromString(
                "[{\"org_id\": 1,\"org_name\": \"test org 1\",\"org_email\": \"email1\",\"role\": \"test role 1\"}, {\"org_id\": 2,\"org_name\": \"test org 2\",\"org_email\": \"email2\",\"role\": \"test role 2\"}]");

        assertThat(orgs.size(), is(2));
        assertThat(orgs.get(0).getOrgId(), is(1));
        assertThat(orgs.get(0).getOrgName(), is("test org 1"));
        assertThat(orgs.get(0).getOrgEmail(), is("email1"));
        assertThat(orgs.get(0).getRole(), is("test role 1"));
        assertThat(orgs.get(1).getOrgId(), is(2));
        assertThat(orgs.get(1).getOrgName(), is("test org 2"));
        assertThat(orgs.get(1).getOrgEmail(), is("email2"));
        assertThat(orgs.get(1).getRole(), is("test role 2"));
    }

    @Test
    void listFromStringEmptyList() {
        List<OrganizationObject> orgs = OrganizationObject.listFromString("[]");

        assertThat(orgs.size(), is(0));
    }

    @Test
    void listFromStringEmpty() {
        List<OrganizationObject> orgs = OrganizationObject.listFromString(null);

        assertThat(orgs.size(), is(0));
    }

    @Test
    void listFromStringInvalid() {
        List<OrganizationObject> orgs = OrganizationObject.listFromString("invalid");

        assertThat(orgs.size(), is(0));
    }

    @Test
    void fromString() {
        OrganizationObject org = OrganizationObject.fromString(
                "{\"org_id\": 1, \"org_name\": \"test org 1\", \"org_email\": \"email\", \"role\": \"test role 1\"}");

        assertThat(org.getOrgId(), is(1));
        assertThat(org.getOrgName(), is("test org 1"));
        assertThat(org.getOrgEmail(), is("email"));
        assertThat(org.getRole(), is("test role 1"));
    }

    @Test
    void fromStringInvalid() {
        OrganizationObject org = OrganizationObject.fromString("invalid");

        assertThat(org, is(nullValue()));
    }

    @Test
    void toStringList() {
        List<OrganizationObject> orgs = List.of(new OrganizationObject(1, "test org 1", "email1", "test role 1"),
                new OrganizationObject(2, "test org 2", "email2", "test role 2"));

        String json = OrganizationObject.toStringList(orgs);

        assertThat(json, is(
                "[{\"org_id\":1,\"org_name\":\"test org 1\",\"org_email\":\"email1\",\"role\":\"test role 1\"},{\"org_id\":2,\"org_name\":\"test org 2\",\"org_email\":\"email2\",\"role\":\"test role 2\"}]"));
    }

    @Test
    void toStringListEmpty() {
        List<OrganizationObject> orgs = List.of();

        String json = OrganizationObject.toStringList(orgs);

        assertThat(json, is("[]"));
    }

    @Test
    void toStringListNull() {
        String json = OrganizationObject.toStringList(null);

        assertThat(json, is("[]"));
    }

    @Test
    void toMap() {
        OrganizationObject org = new OrganizationObject(1, "test org 1", "email1", "test role 1");

        Map<String, Object> map = OrganizationObject.toMap(org);

        assertThat(map.get("org_id"), is(1));
        assertThat(map.get("org_name"), is("test org 1"));
        assertThat(map.get("org_email"), is("email1"));
        assertThat(map.get("role"), is("test role 1"));
    }

    @Test
    void toMapNull() {
        Map<String, Object> map = OrganizationObject.toMap(null);

        assertThat(map.size(), is(0));
    }

    @Test
    void mapListFromString() {
        List<Map<String, Object>> maps = OrganizationObject.mapListFromString(
                "[{\"org_id\": 1,\"org_name\": \"test org 1\",\"org_email\": \"email1\",\"role\": \"test role 1\"}, {\"org_id\": 2,\"org_name\": \"test org 2\",\"org_email\": \"email2\",\"role\": \"test role 2\"}]");

        assertThat(maps.size(), is(2));
        assertThat(maps.get(0).get("org_id"), is(1));
        assertThat(maps.get(0).get("org_name"), is("test org 1"));
        assertThat(maps.get(0).get("org_email"), is("email1"));
        assertThat(maps.get(0).get("role"), is("test role 1"));
        assertThat(maps.get(1).get("org_id"), is(2));
        assertThat(maps.get(1).get("org_name"), is("test org 2"));
        assertThat(maps.get(1).get("org_email"), is("email2"));
        assertThat(maps.get(1).get("role"), is("test role 2"));
    }

    @Test
    void mapListFromStringEmptyList() {
        List<Map<String, Object>> maps = OrganizationObject.mapListFromString("[]");

        assertThat(maps.size(), is(0));
    }

    @Test
    void mapListFromStringEmpty() {
        List<Map<String, Object>> maps = OrganizationObject.mapListFromString(null);

        assertThat(maps.size(), is(0));
    }

    @Test
    void mapListFromStringInvalid() {
        List<Map<String, Object>> maps = OrganizationObject.mapListFromString("invalid");

        assertThat(maps.size(), is(0));
    }
}
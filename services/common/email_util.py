import os
from common.pgquery import query_db


def build_user_email_list(msg_type, org_name):
    # Build the user email query based on the availability of an organization name
    schema_name = os.getenv("POSTGRES_SCHEMA_NAME", "public")
    email_query = (
        "SELECT to_jsonb(row) FROM ("
        f"SELECT email FROM {schema_name}.users "
        "WHERE user_id IN ("
        f"SELECT user_id FROM {schema_name}.user_email_notification "
        "WHERE email_type_id = ("
        f"SELECT email_type_id FROM {schema_name}.email_type WHERE email_type = '{msg_type}'"
        "))"
    )
    if org_name:
        email_query += (
            " AND user_id IN ("
            f"SELECT user_id FROM {schema_name}.user_organization "
            "WHERE organization_id = ("
            f"SELECT organization_id FROM {schema_name}.organizations WHERE name = '{org_name}'"
            "))"
        )
    email_query += ") as row"

    data = query_db(email_query)

    email_list = []
    for row in data:
        row = dict(row[0])
        email_list.append(row["email"])
    return email_list


def build_org_email_list(org_name):
    schema_name = os.getenv("POSTGRES_SCHEMA_NAME", "public")
    email_query = (
        "SELECT to_jsonb(row) FROM ("
        f"SELECT email FROM {schema_name}.organizations WHERE name = '{org_name}'"
        ") as row"
    )

    data = query_db(email_query)

    email_list = []
    for row in data:
        row = dict(row[0])
        email = row["email"]
        if email:
            if email.strip() != "":
                email_list.append(row["email"])
    return email_list


def get_email_list(msg_type, org_name=None):
    email_list = []

    # Verify presence of organization email if org_name is provided
    if org_name:
        email_list = build_org_email_list(org_name)

    # Generate the email list based off of user email preferences if no organization email is configured
    if len(email_list) == 0:
        email_list = build_user_email_list(msg_type, org_name)

    # Only return unique emails
    return list(set(email_list))


def get_email_list_from_rsu(msg_type, rsu_ip):
    schema_name = os.getenv("POSTGRES_SCHEMA_NAME", "public")
    email_query = (
        "SELECT to_jsonb(row) FROM ("
        f"SELECT name, email FROM {schema_name}.organizations "
        "WHERE organization_id IN ("
        f"SELECT organization_id FROM {schema_name}.rsu_organization "
        "WHERE rsu_id IN ("
        f"SELECT rsu_id FROM {schema_name}.rsus WHERE ipv4_address = '{rsu_ip}'"
        "))) as row"
    )

    data = query_db(email_query)

    org_email_list = {}
    # Grab the organization emails if they are configured with a non-None or empty string
    for row in data:
        row = dict(row[0])
        org_email_list[row["name"]] = []
        email = row["email"]

        if email:
            if email.strip() != "":
                org_email_list[row["name"]].append(row["email"])

    email_list = []
    # Combine all of the emails into one email list
    for org_name, org_emails in org_email_list.items():
        # If an organization email isn't configured, grab the configured user emails based on org_name
        if len(org_emails) == 0:
            email_list = email_list + build_user_email_list(msg_type, org_name)
        else:
            email_list = email_list + org_emails

    # Only return unique emails
    return list(set(email_list))

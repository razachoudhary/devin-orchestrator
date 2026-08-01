create table remediations (
    id                  bigserial primary key,
    issue_number        integer not null,
    issue_title         text not null,
    source              text not null,          -- SCOUT | MANUAL
    state               text not null,
    devin_session_id    text,
    devin_session_url   text,
    pr_url              text,
    pr_number           integer,
    status_comment_id   bigint,
    acus_consumed       numeric(10,2),
    structured_output   jsonb,
    repair_attempts     integer not null default 0,
    escalation_reason   text,
    version             integer not null default 0,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

-- semantic dedupe: at most one live remediation per issue
create unique index uq_remediation_active_issue
    on remediations (issue_number)
    where state not in ('GATED_REJECTED','MERGED','ESCALATED','FAILED');

create index idx_remediation_state on remediations (state);
create index idx_remediation_pr on remediations (pr_number);

create table state_transitions (
    id              bigserial primary key,
    remediation_id  bigint not null references remediations(id) on delete cascade,
    from_state      text,
    to_state        text not null,
    reason          text,
    occurred_at     timestamptz not null default now()
);

create index idx_transition_remediation on state_transitions (remediation_id);
create index idx_transition_occurred on state_transitions (occurred_at);

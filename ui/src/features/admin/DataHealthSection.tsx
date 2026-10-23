import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { DataHealthGolfer, DataHealthReport, RosteredTwinGroup } from '@/shared/api/types';
import QueryState from '@/shared/components/QueryState';
import AdminSection from './AdminSection';

function DataHealthSection(): ReactNode {
  const healthQuery = useQuery<DataHealthReport>({
    queryKey: queryKeys.dataHealth.root,
    queryFn: api.dataHealth,
  });

  return (
    <AdminSection
      title="Golfer Data Health"
      description={
        <>
          Golfer rows that score wrongly until an operator fixes them. Currently rostered means on a team in its
          league&apos;s latest season.
        </>
      }
    >
      <QueryState query={healthQuery} label="golfer data health">
        {(report) => (
          <div className="space-y-6">
            <HealthCheck
              title="Rostered twins"
              description={
                'A rostered golfer with no ESPN id shares a name with one that has it, so ESPN ' +
                'results go to the twin.'
              }
              count={report.rosteredTwins.count}
            >
              {report.rosteredTwins.rows.map((group) => (
                <TwinGroupRow key={group.normalizedName} group={group} />
              ))}
            </HealthCheck>
            <HealthCheck
              title="Rostered without an ESPN id"
              description="Currently rostered golfers that ESPN results can reach only by name."
              count={report.rosteredWithoutEspnId.count}
            >
              {report.rosteredWithoutEspnId.rows.map((golfer) => (
                <li key={golfer.id}>
                  <GolferLabel golfer={golfer} />
                </li>
              ))}
            </HealthCheck>
            <HealthCheck
              title="Blank first names"
              description="Golfers that name matching can reach by last name alone."
              count={report.blankFirstNames.count}
            >
              {report.blankFirstNames.rows.map((golfer) => (
                <li key={golfer.id}>
                  <GolferLabel golfer={golfer} />
                </li>
              ))}
            </HealthCheck>
          </div>
        )}
      </QueryState>
    </AdminSection>
  );
}

interface HealthCheckProps {
  title: string;
  description: string;
  count: number;
  children: ReactNode;
}

function HealthCheck({ title, description, count, children }: HealthCheckProps): ReactNode {
  return (
    <section aria-label={title}>
      <h4 className="text-sm font-semibold text-ink-strong">
        {title} <span className="text-ink-muted font-normal">({count})</span>
      </h4>
      <p className="text-xs text-ink-muted mb-2">{description}</p>
      {count === 0 ? (
        <p className="text-sm text-semantic-success">None found.</p>
      ) : (
        <ul className="space-y-1 text-sm text-ink-body">{children}</ul>
      )}
    </section>
  );
}

function TwinGroupRow({ group }: { group: RosteredTwinGroup }): ReactNode {
  return (
    <li>
      <span className="font-medium">{group.normalizedName}</span>
      <ul className="ml-4 space-y-0.5">
        {group.rosteredWithoutEspnId.map((golfer) => (
          <li key={golfer.id}>
            Rostered: <GolferLabel golfer={golfer} />
          </li>
        ))}
        {group.holdingEspnId.map((golfer) => (
          <li key={golfer.id}>
            Takes results: <GolferLabel golfer={golfer} />
          </li>
        ))}
      </ul>
    </li>
  );
}

function GolferLabel({ golfer }: { golfer: DataHealthGolfer }): ReactNode {
  const name = golfer.firstName.trim() === '' ? `(blank) ${golfer.lastName}` : `${golfer.firstName} ${golfer.lastName}`;
  return (
    <>
      {name}
      {golfer.pgaPlayerId === null ? null : <span className="text-ink-muted"> · ESPN {golfer.pgaPlayerId}</span>}
      <span className="text-xs text-ink-faint font-mono"> {golfer.id}</span>
    </>
  );
}

export default DataHealthSection;

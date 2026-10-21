import type { ReactNode } from 'react';

interface AdminSectionProps {
  readonly title: string;
  readonly description?: ReactNode;
  readonly children: ReactNode;
}

function AdminSection({ title, description, children }: AdminSectionProps): ReactNode {
  return (
    <div className="bg-surface-card rounded-lg p-6">
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">{title}</h3>
      {description === undefined ? null : <p className="text-xs text-ink-muted mb-4">{description}</p>}
      {children}
    </div>
  );
}

export default AdminSection;

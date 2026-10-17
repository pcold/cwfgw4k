import type { ReactNode } from 'react';

interface PageTitleProps {
  readonly title: string;
  readonly subtitle?: ReactNode;
  /** The status chip and the league/season picker. Below `sm:` they wrap onto a line of their own. */
  readonly actions?: ReactNode;
}

/** An `h2`, because the app's `h1` is the CASTLEWOOD FG mark in the nav. */
function PageTitle({ title, subtitle, actions }: PageTitleProps): ReactNode {
  return (
    <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
      <div className="min-w-0">
        <h2 className="font-display italic font-extrabold uppercase text-2xl sm:text-3xl leading-tight text-ink-strong">
          {title}
        </h2>
        {subtitle ? <p className="text-sm text-ink-muted">{subtitle}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-3 w-full sm:w-auto">{actions}</div> : null}
    </header>
  );
}

export default PageTitle;

import type {
  RuleGroupPresentation,
  RuleSchemeSummary,
} from '@shared/index';

export * from '@shared/index';

export interface RuleSchemesResponseLike {
  bundledSchemes?: RuleSchemeSummary[];
  importedSchemes?: RuleSchemeSummary[];
  selectedPresetID?: string;
  activeSchemeGroups?: RuleGroupPresentation[];
}

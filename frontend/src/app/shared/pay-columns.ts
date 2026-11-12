/**
 * Column names and their plain-language meaning (shown on hover), shared by every pay table so they all
 * read the same way. Statistical terms are deliberately avoided in the labels.
 */
export const PAY_COLUMNS = {
  people: { label: 'People', hint: 'Number of active employees in the group' },
  min: { label: 'Lowest', hint: 'The lowest salary in the group' },
  range: {
    label: 'Typical range (middle half)',
    hint: 'What the middle half of people earn: a quarter earn less than the first figure, a quarter earn more than the second (25th to 75th percentile)',
  },
  median: { label: 'Typical (median)', hint: 'The middle salary: half of people earn less, half earn more' },
  max: { label: 'Highest', hint: 'The highest salary in the group' },
  average: { label: 'Average', hint: 'Total pay divided by the number of people. Use it for budgets' },
  bar: {
    label: 'Range',
    hint: 'Lowest to highest, split into quarters of the people in the group (lightest to darkest). The dark mark and bold figure are the typical (median) salary. Each bar is scaled to its own row; hover a bar for all five figures',
  },
  medianUsd: {
    label: 'Typical (USD)',
    hint: 'The typical (median) salary converted to USD, so countries can be compared',
  },
} as const;

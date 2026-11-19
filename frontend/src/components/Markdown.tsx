import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import 'highlight.js/styles/github.css';

/**
 * 统一的 Markdown 渲染组件（#48）。
 *
 * 安全说明：react-markdown 默认不渲染原始 HTML，正文里的 `<script>` 只会按纯文本显示；
 * 因此这里**不引入** `rehype-raw`，避免用户输入被当成 HTML 执行（XSS 回归项）。
 *
 * @昵称高亮由 MentionText 只处理评论等非 Markdown 字段，Markdown 渲染后文本会被拆分，不在此处理。
 */
export function Markdown({ value }: { value: string }) {
  return (
    <div className="markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
        {value}
      </ReactMarkdown>
    </div>
  );
}
